package ir.ashkan.shahnameh

import cats.effect.{Concurrent, Ref}
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}
import Connection._
import cats.{Applicative, FlatMap}
import cats.syntax.all._

class Port[F[_] :Concurrent :FlatMap](
  connections: Ref[F, Map[ConnectionId, Topic[F, String]]],
  genId: F[ConnectionId],
  incomingTopic: Topic[F, Incoming]
) {
  def connect: F[Connection[F]] =
    for {
      id <- genId
      topic <- Topic[F, String]
      _ <- connections.update(_.updated(id, topic))
      receive: Pipe[F, String, Unit] = (_: Stream[F, String]).map(Incoming(id, _)).through(incomingTopic.publish)
      send = topic.subscribe(10)
    } yield Connection(send, receive)

  def send(s: Stream[F, Outgoing]): Stream[F, Unit] = s.through(_.evalMap {
    case Outgoing(to, msg) => connections.get.flatMap(_.get(to).fold(Applicative[F].unit)(_.publish1(msg).void))
  })

  def receive(s: Pipe[F, Incoming, _]): Stream[F, _] = incomingTopic.subscribe(10).through(s)
}

object Port {
  def apply[F[_] :Concurrent :FlatMap]: F[Port[F]] =
    for {
      incomingTopic <- Topic[F, Incoming]
      connections <- Ref[F].of(Map.empty[ConnectionId, Topic[F, String]])
      getConnectionId = Ref[F].of(0L).flatMap(_.updateAndGet(_ + 1))
    } yield new Port(connections, getConnectionId, incomingTopic)
}