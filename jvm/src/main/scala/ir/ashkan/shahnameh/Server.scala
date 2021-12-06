package ir.ashkan.shahnameh.demo

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.effect.std.Console
import cats.syntax.flatMap._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import ir.ashkan.shahnameh.{Config, Connection, GoogleOIDC, Port, UserService}
import org.http4s.headers.Location
import org.http4s.{AuthedRoutes, HttpApp, HttpRoutes, Request, Response, ResponseCookie, StaticFile, Uri}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame._
import cats.syntax.semigroupk._
import ir.ashkan.shahnameh.UserService.User
import org.http4s.server.staticcontent.resourceServiceBuilder

object WebSocketServer extends IOApp {
  import org.http4s.dsl.io._

  private def googleOCIDRoutes(googleOIDC: GoogleOIDC, users: UserService[IO]): HttpRoutes[IO] = {
    object StateParam extends QueryParamDecoderMatcher[String]("state")
    object CodeParam extends QueryParamDecoderMatcher[String]("code")

    HttpRoutes.of {
      case GET -> Root / "login" =>
        SeeOther.headers(Location(googleOIDC.authenticationUri))

      case req @ GET -> Root / "googleOpenIdConnect" :? StateParam(_) +& CodeParam(code) =>
        for {
          user <- googleOIDC.authenticate[IO](code) >>= users.findOrInsert
          index = req.uri.withPath(Uri.Path.unsafeFromString("/"))
          sessionId <- users.login(user.id)
          cookie = ResponseCookie("sessionId", sessionId.toString)
          res <- SeeOther.headers(Location(index)).map(_.addCookie(cookie))
        } yield res
    }
  }

  def auth(users: UserService[IO]): AuthMiddleware[IO, User] = {
    import mouse.any._

    def authUser(req: Request[IO]): OptionT[IO, User] =
      req.cookies.find(_.name == "sessionId").map(_.content |> users.findUser).getOrElse(OptionT.none)

    AuthMiddleware.withFallThrough(Kleisli(authUser))
  }

  private def userRoutes(users: UserService[IO]): AuthedRoutes[User, IO] =
    AuthedRoutes.of[User, IO] {
      case req @ GET -> Root / "logout" as user =>
        val login = req.req.uri.withPath(Uri.Path.unsafeFromString("login"))
        users.logout(user.id) *> SeeOther.headers(Location(login))
    }

  private def websocketsRoutes(connect: IO[Connection[IO]], builder: WebSocketBuilder2[IO]): AuthedRoutes[User, IO] =
    AuthedRoutes.of[User, IO] {
      case GET -> Root / "ws" as user =>
        connect.flatMap {
          case Connection(send, receive) =>
            builder.build(
              send.map(Text(_)),
              _.collect { case Text(str, true) => str }.through(receive)
            )
        }
    }

  def database(config: Config.Database): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        config.url,
        config.username,
        config.password,
        ce
      )
    } yield xa


  private def migrate(xa: HikariTransactor[IO]): IO[Unit] =
    IO(org.flywaydb.core.Flyway.configure().dataSource(xa.kernel).load().migrate()).void

  def run(config: Config.Application): IO[ExitCode] =
    for {
      port <- Port()

      googleOIDC <- GoogleOIDC.fromConfig[IO](config.googleOpenidConnect)

      _ <- database(config.database).use { db =>
        val users = UserService.doobie[IO](db)
        val gRoutes = googleOCIDRoutes(googleOIDC, users)

        val healths = HttpRoutes.of[IO] {
          case GET -> Root / "healthz" => Ok()
        }

        val index = HttpRoutes.of[IO] {
          case req @ GET -> Root => StaticFile.fromResource[IO]("index.html", Some(req)).getOrElseF(NotFound())
        }

        val assets = Router[IO]("assets" -> resourceServiceBuilder("assets").toRoutes)

        val routes = gRoutes <+> auth(users)(userRoutes) <+> healths <+> assets <+> index
        val wsRoute = (b: WebSocketBuilder2[IO]) => auth(users)(websocketsRoutes(port.connect, b))

        migrate(db) *>
          BlazeServerBuilder[IO]
            .bindHttp(config.http.webSocketPort, "0.0.0.0")
            .withHttpWebSocketApp(wsRoute(_).orNotFound)
            .withHttpApp(routes.orNotFound)
            .serve
            .compile
            .drain
        }

      //      sends <- (config.kafka |> Kafka.send |> port.send).compile.drain.start
      //      receives <- (config.kafka |> Kafka.receive |> port.receive).compile.drain.start


      //        .guarantee(sends.cancel)
      //        .guarantee(receives.cancel)

      _ <- Console[IO].println("Terminated")
    } yield ExitCode.Success
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


  override def run(args: List[String]): IO[ExitCode] = Config.load[IO].flatMap(run)
}
