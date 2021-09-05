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

  case class Incoming(from: ConnectionId, text: String)

  case class Outgoing(to: ConnectionId, text: String)

  case class Connection[F[_]](send: Stream[F, String], receive: Pipe[F, String, Unit])

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
            case Connection(send, receive) =>
              WebSocketBuilder[F].build(
                send.map(Text(_)),
                _.collect { case Text(str, true) => str }.through(receive)
              )
          }
      }

    def fromKafka(config: Config.Kafka): Stream[IO, Outgoing] = {
      val settings = ConsumerSettings[IO, Unit, Outgoing]
        .withAutoOffsetReset(AutoOffsetReset.Latest)
        .withBootstrapServers(config.bootstrapServers)
        .withGroupId("outgoing-consumer-group")

      KafkaConsumer.stream(settings)
        .evalTap(_.subscribeTo(config.outgoingTopic))
        .flatMap(_.stream)
        .map(_.record.value)
    }

    def toKafka(config: Config.Kafka): Pipe[IO, Incoming, _] = { incoming =>
      val records = incoming.map(msg => ProducerRecord(config.topic, (), msg) |> ProducerRecords.one)
      val settings = ProducerSettings[IO, Unit, Incoming].withBootstrapServers(config.bootstrapServers)
      KafkaProducer.stream(settings).flatMap(p => records.evalMap(r => p.produce(r)))
    }

    class Port(
       connections: Ref[IO, Map[ConnectionId, Topic[IO, String]]],
       genId: IO[ConnectionId],
       incoming: Pipe[IO, Incoming, Nothing]
     ) {

      def send: Pipe[IO, Outgoing, Unit] = _.evalMap {
        case Outgoing(to, msg) => connections.get.flatMap(_.get(to).fold(IO.unit)(_.publish1(msg).void))
      }

      def connect: IO[Connection[IO]] =
        for {
          id <- genId
          topic <- Topic[IO, String]
          _ <- connections.update(_.updated(id, topic))
          receive: Pipe[IO, String, Unit] = (_: Stream[IO, String]).map(Incoming(id, _)).through(incoming)
          send = topic.subscribe(10)
        } yield Connection(send, receive)
    }

    object Port {
      def apply(incoming: Pipe[IO, Incoming, Nothing]): IO[Port] =
        for {
          connections <- Ref[IO].of(Map.empty[ConnectionId, Topic[IO, String]])
          getConnectionId = Ref[IO].of(0L).flatMap(_.updateAndGet(_ + 1))
        } yield new Port(connections, getConnectionId, incoming)
    }

    for {
      config <- Config.load[IO]

      incomingTopic <- Topic[IO, Incoming]
      incoming: Stream[IO, Incoming] = incomingTopic.subscribe(10)

      port <- Port(incomingTopic.publish)

      sends <- fromKafka(config.kafka).through(port.send).compile.drain.start
      receives <- incoming.through(toKafka(config.kafka)).compile.drain.start

      _ <- BlazeServerBuilder[IO](global)
        .bindHttp(config.http.webSocketPort)
        .withHttpApp(routes[IO](port.connect).orNotFound)
        .serve
        .compile
        .drain
        .guarantee(sends.cancel)
        .guarantee(receives.cancel)

      _ <- Console[IO].println("Terminated")
    } yield ExitCode.Success
  }

}
