package ir.ashkan.shahnameh

import cats.effect.{IO, Ref}
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import Connection._

class Port(
  connections: Ref[IO, Map[ConnectionId, Topic[IO, String]]],
  genId: IO[ConnectionId],
  incomingTopic: Topic[IO, Incoming]
) {
  def connect: IO[Connection[IO]] =
    for {
      id <- genId
      topic <- Topic[IO, String]
      _ <- connections.update(_.updated(id, topic))
      receive: Pipe[IO, String, Unit] = (_: Stream[IO, String]).map(Incoming(id, _)).through(incomingTopic.publish)
      send = topic.subscribe(10)
    } yield Connection(send, receive)

  def send(s: Stream[IO, Outgoing]): Stream[IO, Unit] = s.through(_.evalMap {
    case Outgoing(to, msg) => connections.get.flatMap(_.get(to).fold(IO.unit)(_.publish1(msg).void))
  })

  def receive(s: Pipe[IO, Incoming, _]): Stream[IO, _] = incomingTopic.subscribe(10).through(s)
}

object Port {
  def apply(): IO[Port] =
    for {
      incomingTopic <- Topic[IO, Incoming]
      connections <- Ref[IO].of(Map.empty[ConnectionId, Topic[IO, String]])
      getConnectionId = Ref[IO].of(0L).flatMap(_.updateAndGet(_ + 1))
    } yield new Port(connections, getConnectionId, incomingTopic)
}