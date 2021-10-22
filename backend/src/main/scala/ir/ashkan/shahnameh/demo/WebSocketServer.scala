package ir.ashkan.shahnameh.demo

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.effect.std.Console
import cats.syntax.flatMap._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import ir.ashkan.shahnameh.{Config, Connection, GoogleOIDC, Port, UserService}
import org.http4s.headers.Location
import org.http4s.{AuthedRoutes, HttpRoutes, Request, ResponseCookie, Uri}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.AuthMiddleware
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame._
import cats.syntax.semigroupk._
import ir.ashkan.shahnameh.UserService.User

import scala.concurrent.ExecutionContext.global


object WebSocketServer extends IOApp {
  import org.http4s.dsl.io._

  private def googleOCIDRoutes(googleOIDC: GoogleOIDC, users: UserService[IO]): HttpRoutes[IO] = {
    object StateParam extends QueryParamDecoderMatcher[String]("state")
    object CodeParam extends QueryParamDecoderMatcher[String]("code")

    HttpRoutes.of {
      case GET -> Root / "login" =>
        SeeOther.headers(Location(googleOIDC.authenticationUri))

      case req@GET -> Root / "googleOpenIdConnect" :? StateParam(_) +& CodeParam(code) =>
        for {
          user <- googleOIDC.authenticate[IO](code) >>= users.findOrInsert
          redirectTo = req.uri.withPath(Uri.Path.unsafeFromString("me"))
          cookie = ResponseCookie("authcookie", user.email)
          res <- SeeOther.headers(Location(redirectTo)).map(_.addCookie(cookie))
        } yield res

      case GET -> Root / "healthz" =>
        Ok()
    }
  }


  private def userRoutes(users: UserService[IO]): HttpRoutes[IO] = {
    import org.http4s.dsl.io._
    import mouse.any._

    def authUser(req: Request[IO]): OptionT[IO, User] =
      req.cookies.find(_.name == "authcookie").map(_.content |> users.findUser).getOrElse(OptionT.none)

    val auth = AuthMiddleware.withFallThrough(Kleisli(authUser))

    val routes = AuthedRoutes.of[User, IO] {
      case GET -> Root / "me" as user => Ok(user.toString)
      case GET -> Root / "welcome" as user => Ok(s"Welcome, ${user.name}")
      case GET -> Root / "logout" as user => Ok(user.toString).map(_.removeCookie("authcookie"))
    }

    auth(routes)
  }

  private def websocketsRoutes(connect: IO[Connection[IO]]): HttpRoutes[IO] =
    HttpRoutes.of {
      case GET -> Root / "ws" =>
        connect.flatMap {
          case Connection(send, receive) =>
            WebSocketBuilder[IO].build(
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
      googleOIDC <- GoogleOIDC.fromConfig[IO](config.googleOpenidConnect)
      port <- Port()
      _ <- database(config.database).use { db =>
          val users = UserService.doobie[IO](db)
          val r = googleOCIDRoutes(googleOIDC, users)
          val routes = r <+> websocketsRoutes(port.connect) <+> userRoutes(users)

        migrate(db) *>
          BlazeServerBuilder[IO](global)
            .bindHttp(config.http.webSocketPort, "0.0.0.0")
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
