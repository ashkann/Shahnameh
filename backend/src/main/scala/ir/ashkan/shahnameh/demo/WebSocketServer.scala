package ir.ashkan.shahnameh.demo

import cats.effect._
import cats.effect.std.Console
import cats.syntax.flatMap._
import ir.ashkan.shahnameh.{Config, Connection, GoogleOIDC, Port}
import org.http4s.headers.Location
//import mouse.any._
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame._

import scala.concurrent.ExecutionContext.global


object WebSocketServer extends IOApp {
  def app[F[_] : Async](connect: F[Connection[F]], googleOIDC: GoogleOIDC): HttpRoutes[F] = {
    import org.http4s.QueryParamDecoder._
    val dsl = org.http4s.dsl.Http4sDsl[F]
    import dsl._

    object StateParam extends QueryParamDecoderMatcher[String]("state")
    object CodeParam extends QueryParamDecoderMatcher[String]("code")

    HttpRoutes.of {
      case GET -> Root / "ws" =>
        connect.flatMap {
          case Connection(send, receive) =>
            WebSocketBuilder[F].build(
              send.map(Text(_)),
              _.collect { case Text(str, true) => str }.through(receive)
            )
        }

      case GET -> Root / "login" => SeeOther.headers(Location(googleOIDC.authenticationUri))

      case GET -> Root / "googleOpenIdConnect" :? StateParam(_) +& CodeParam(code) =>
        googleOIDC.authenticate(code).flatMap(user => Ok(user.toString))

      case GET -> Root / "healthz" =>
        Ok()
    }
  }

  def run(config: Config.Application): IO[ExitCode] =
    for {
      port <- Port()

      //      sends <- (config.kafka |> Kafka.send |> port.send).compile.drain.start
      //      receives <- (config.kafka |> Kafka.receive |> port.receive).compile.drain.start

      googleOIDC <- GoogleOIDC.fromConfig[IO](config.googleOpenidConnect)
      _ <- BlazeServerBuilder[IO](global)
        .bindHttp(config.http.webSocketPort, "0.0.0.0")
        .withHttpApp(app[IO](port.connect, googleOIDC).orNotFound)
        .serve
        .compile
        .drain
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
