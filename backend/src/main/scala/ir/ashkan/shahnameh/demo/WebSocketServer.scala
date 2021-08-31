package ir.ashkan.shahnameh.demo

import cats.Functor
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.effect._
import cats.effect.std.{Console, Queue}
import fs2._
import fs2.concurrent.Topic
import fs2.kafka.{AutoOffsetReset, ConsumerSettings, KafkaConsumer, KafkaProducer, ProducerRecord, ProducerRecords, ProducerResult, ProducerSettings}
import ir.ashkan.shahnameh.Config
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import mouse.any._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global

object WebSocketServer extends IOApp {
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

  type ConnectionId = Long

  case class Incoming(sender: ConnectionId, text: String)

  case class Outgoing(recipient: ConnectionId, text: String)

  case class Connection[F[_]](outgoing: Stream[F, String], incoming: Pipe[F, String, Unit])

  //  trait Ashkan[F[_]] {
  ////    def send(to: ClientId, msg: String): F[Unit]
  //
  //    val connect: F[Connection[F]]
  //  }

  override def run(args: List[String]): IO[ExitCode] = {
    def routes[F[_] : Async](connect: F[Connection[F]]): HttpRoutes[F] =
      HttpRoutes.of {
        case GET -> Root / "ws" =>
          connect.flatMap {
            case Connection(outgoing, incoming) =>
              WebSocketBuilder[F].build(
                outgoing.map(Text(_)),
                _.collect { case Text(str, true) => str }.through(incoming)
              )
          }
      }

    for {
      config <- Config.load[IO]

      outgoing = {
        val settings = ConsumerSettings[IO, Unit, Outgoing]
          .withAutoOffsetReset(AutoOffsetReset.Latest)
          .withBootstrapServers(config.kafka.bootstrapServers)
          .withGroupId("outgoing-consumer-group")

        KafkaConsumer.stream(settings)
          .evalTap(_.subscribeTo(config.kafka.outgoingTopic))
          .flatMap(_.stream)
          .map(_.record.value)
      }

      lastConnectionId <- Ref[IO].of(0)
      connections <- Ref[IO].of(Map.empty[ConnectionId, Topic[IO, String]])

      o = outgoing.evalMap {
        case Outgoing(to, text) => connections.get.flatMap(_.get(to).fold(IO.unit)(_.publish1(text).void))
      }

      incoming <- Topic[IO, Incoming].map(_.publish)
      a = {
        val records = topic.subscribe(10)
          .map(msg => ProducerRecord(config.kafka.topic, (), msg) |> ProducerRecords.one)

        val settings = ProducerSettings[IO, Unit, Incoming].withBootstrapServers(config.kafka.bootstrapServers)
        val incoming = KafkaProducer.stream(settings).flatMap(p => records.evalMap(p.produce))
      }

      connect = for {
        topic <- Topic[IO, String]
        connectionId <- lastConnectionId.updateAndGet(_ + 1)
        _ <- connections.update(_.updated(connectionId, topic))
        send = topic.subscribe(10)
        receive: Pipe[IO, String, Unit] = (_: Stream[IO, String]).map(Incoming(connectionId, _)).through(incoming)
      } yield Connection(send, receive)

      _ <- BlazeServerBuilder[IO](global)
        .bindHttp(config.http.webSocketPort)
        .withHttpApp(routes[IO](connect).orNotFound)
        .serve
        .compile
        .drain

      _ <- Console[IO].println("Terminated")
    } yield ExitCode.Success
  }
}
