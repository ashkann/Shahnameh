package ir.ashkan.shahnameh

import fs2.{Pipe, Stream}

case class Connection[F[_]](send: Stream[F, String], receive: Pipe[F, String, Unit])

object Connection {
  type ConnectionId = Long

  case class Incoming(from: ConnectionId, text: String)

  case class Outgoing(to: ConnectionId, text: String)
}