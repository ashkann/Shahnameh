package ir.ashkan.shahnameh

import cats.Monad
import cats.data.Kleisli
import cats.effect._
import cats.effect.std.Console
import cats.syntax.all._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import mouse.any._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.{Http4sDsl => Dsl}
import org.http4s.headers.Location
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame._
import org.http4s.{AuthedRoutes, HttpRoutes, Uri}

object Server extends IOApp {

  def googleOCIDRoutes[F[_] :Async :Users :Sessions](googleOIDC: GoogleOIDC)(dsl: Dsl[F]): HttpRoutes[F] = {
    import dsl._

    def login(user: GoogleOIDC.User) =
      for {
        usr <- Users[F].findOrInsert(user)
        sessionId = Sessions.generateId
        _ <- Users[F].login(usr.id, sessionId)
      } yield sessionId

    object StateParam extends QueryParamDecoderMatcher[String]("state")
    object CodeParam extends QueryParamDecoderMatcher[String]("code")

    HttpRoutes.of[F] {
      case GET -> Root / "login" => googleOIDC.loginRoute(dsl)

      case req @ GET -> Root / "googleOpenIdConnect" :? StateParam(state) +& CodeParam(code) =>
        for {
          gUser <- googleOIDC.authenticate(code, state)
          sessionId <- login(gUser)
          index = req.uri.withPath(Uri.Path.unsafeFromString("/"))
          res <- SeeOther.headers(Location(index)).map(Sessions[F].write(_, sessionId))
        } yield res
    }
  }

  def userRoutes[F[_] :Monad :Users :Sessions](dsl: Dsl[F]): AuthedRoutes[Users.User, F] = {
    import dsl._

    AuthedRoutes.of[Users.User, F] {
      case req @ GET -> Root / "logout" as user =>
        for {
          _ <- Users[F].logout(user.id)
          login = req.req.uri.withPath(Uri.Path.unsafeFromString("login"))
          res <- SeeOther.headers(Location(login)).map(Sessions[F].remove)
        } yield res
    }
  }

  def websocketsRoutes[F[_] : Monad](connect: F[Connection[F]], builder: WebSocketBuilder2[F], dsl: Dsl[F])
  : AuthedRoutes[Users.User, F] = {
    import dsl._

    AuthedRoutes.of[Users.User, F] {
      case GET -> Root / "ws" as _ =>
        connect.flatMap {
          case Connection(send, receive) =>
            builder.build(
              send.map(Text(_)),
              _.collect { case Text(str, true) => str }.through(receive)
            )
        }
    }
  }

  def database[F[_] : Async](config: Config.Database): Resource[F, HikariTransactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](32)
      xa <- HikariTransactor.newHikariTransactor[F](
        "org.postgresql.Driver",
        config.url,
        config.username,
        config.password,
        ce
      )
    } yield xa

  def healthzRoute[F[_] : Monad](dsl: Dsl[F]): HttpRoutes[F] = {
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "healthz" => Ok() }
  }

  def run[F[_] :Async :Sessions :Users :Console](config: Config.Application): F[Unit] =
    for {
      port <- Port[F]
      dsl = Dsl[F]

      googleOIDC <- GoogleOIDC.fromConfig[F](config.googleOpenidConnect)

      gRoutes = googleOCIDRoutes[F](googleOIDC)(dsl)

      auth = AuthMiddleware.withFallThrough(Kleisli(Auth.fromCookie.auth))

      routes = gRoutes <+> auth(userRoutes(dsl)) <+> healthzRoute(dsl)
      wsRoute = (b: WebSocketBuilder2[F]) => auth(websocketsRoutes(port.connect, b, dsl))

      _ <- BlazeServerBuilder[F]
        .bindHttp(config.http.webSocketPort, "0.0.0.0")
        .withHttpWebSocketApp(wsRoute(_).orNotFound)
        .withHttpApp(routes.orNotFound)
        .serve
        .compile
        .drain
      //      sends <- (config.kafka |> Kafka.send |> port.send).compile.drain.start
      //      receives <- (config.kafka |> Kafka.receive |> port.receive).compile.drain.start


      //        .guarantee(sends.cancel)
      //        .guarantee(receives.cancel)

      _ <- Console[F].println("Terminated")
    } yield ()
  //  sealed trait State
  //  object State {
  //    type Character = Nothing
  //    type Action = Nothing
  //    type CompleteAction = Nothing
  //
  //    case object Ready extends State
  //    case class MyTurn(character: Character) extends State
  //    case class OpponentTurn(character: Character) extends State
  //    case class SelectedCharacter(character: Character) extends State
  //    case class SelectedAction(action: Action) extends State
  //    case class SelectedTarget(target: Character) extends State
  //    case object MustSelectCharacter extends State
  //    case object MustSelectAction extends State
  //    case object MustSelectTarget extends State
  //    case class WeHaveACompleteAction(action: CompleteAction) extends State
  //  }

  //         val users = Users.doobie[F](db)

  override def run(args: List[String]): IO[ExitCode] =
    for {
      config <- Config.load[IO]
      _ <- database[IO](config.database).use { db =>
        implicit val sessions = Sessions.http4s[IO]
        implicit val users = Users.doobie[IO](db)

        run[IO](config)
      }
    } yield ExitCode.Success
}
