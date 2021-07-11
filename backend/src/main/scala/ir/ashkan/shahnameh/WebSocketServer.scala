
package ir.ashkan.shahnameh

import cats.Functor
import cats.effect._
import cats.effect.std.Queue
import fs2._
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._

import scala.concurrent.ExecutionContext.global

object WebSocketServer extends IOApp {
  sealed trait State
  object State {
    type Character = Nothing
    type Action = Nothing
    type CompleteAction = Nothing

    case object Ready extends State
    case class MyTurn(character: Character) extends State
    case class OpponentTurn(character: Character) extends State
    case class SelectedCharacter(character: Character) extends State
    case class SelectedAction(action: Action) extends State
    case class SelectedTarget(target: Character) extends State
    case object MustSelectCharacter extends State
    case object MustSelectAction extends State
    case object MustSelectTarget extends State
    case class WeHaveACompleteAction(action: CompleteAction) extends State
  }

  override def run(args: List[String]): IO[ExitCode] = {
    trait Server[F[_]] {
      def receive(msg: String): F[Unit]
      val send: Stream[F, String]
    }

    def routes[F[_] : Async](server: Server[F]): HttpRoutes[F] = {
      val send: Stream[F, WebSocketFrame] = server.send.map(s => Text(s))

      val receive: Pipe[F, WebSocketFrame, Unit] = _.evalMap {
        case Text(t, _) => server.receive(t)
      }

      HttpRoutes.of[F] {
        case GET -> Root / "ws" =>
          WebSocketBuilder[F].build(send, receive)
      }
    }

    class Echo[F[_]: Functor](q: Queue[F, Option[String]]) extends Server[F] {
      override def receive(msg: String): F[Unit] = q.offer(Some(msg))
      override val send: Stream[F, String] = Stream.fromQueueNoneTerminated(q)
    }

    for {
      q <- Queue.bounded[IO, Option[String]](10)
      echo = new Echo(q)
      code <- BlazeServerBuilder[IO](global)
        .bindHttp(8080)
        .withHttpApp(routes(echo).orNotFound)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield code
  }
}
