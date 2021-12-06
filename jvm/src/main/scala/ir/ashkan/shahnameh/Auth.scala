package ir.ashkan.shahnameh

import cats.Monad
import cats.data.OptionT
import ir.ashkan.shahnameh.Users.User
import org.http4s.Request
import simulacrum._

@typeclass
trait Auth[F[_]] {
  def auth(req: Request[F]): OptionT[F, User]
}

object Auth {
  def fromCookie[F[_] :Monad :Sessions :Users]: Auth[F] =
    req => Sessions[F].read(req).fold(OptionT.none[F, User])(Users[F].findLoggedIn)
}