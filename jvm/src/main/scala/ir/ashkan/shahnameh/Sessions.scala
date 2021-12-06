package ir.ashkan.shahnameh

import mouse.any._
import org.http4s.{Request, Response, ResponseCookie}
import simulacrum._

import java.util.UUID
import scala.util.Try

@typeclass
trait Sessions[F[_]] {

  import Sessions.SessionId

  def read(in: Request[F]): Option[SessionId]

  def write(out: Response[F], sessionId: SessionId): Response[F]

  def remove(out: Response[F]): Response[F]
}

object Sessions {
  case class SessionId private(value: UUID)

  def generateId: SessionId = SessionId(UUID.randomUUID())
  def fromString(uuid: String): Option[SessionId] = Try(UUID.fromString(uuid)).toOption.map(SessionId)

  def http4s[F[_]]: Sessions[F] = new Sessions[F] {
    override def read(in: Request[F]): Option[SessionId] =
      in.cookies.find(_.name == "sessionId").flatMap(_.content |> fromString)

    override def write(out: Response[F], id: SessionId): Response[F] =
      out.addCookie(ResponseCookie("sessionId", id.toString))

    override def remove(out: Response[F]): Response[F] =
      out.removeCookie("sessionId")
  }
}