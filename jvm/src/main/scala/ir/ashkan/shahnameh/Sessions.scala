package ir.ashkan.shahnameh

import cats.data.OptionT

import java.util.UUID
import simulacrum._

@typeclass
trait Session[F[_]] {
  import Session._

  val generateId: F[SessionId]
  def fromString(value: String): OptionT[F, SessionId]
}

object Session {
  case class SessionId private (value: UUID)
}