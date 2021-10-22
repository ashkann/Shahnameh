package ir.ashkan.shahnameh

import cats.Monad
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import doobie.implicits._

trait UserService[F[_]] {
  import UserService.User

  def findUser(email: String): OptionT[F, User]

  def insertUser(user: User): F[Unit]

  def registerOrLogin(user: GoogleOIDC.User)(implicit M: Monad[F]): F[User] =
    findUser(user.email).getOrElseF {
      val usr = UserService.fromGoogleOCID(user)
      insertUser(usr)
    }
}

object UserService {
  case class User(id: Long, name: String, email: String, picture: String)

  def fromGoogleOCID(user: GoogleOIDC.User): User =
    User(0, name = user.name, email = user.email, picture = user.picture)

  def doobie[F[_] : MonadCancelThrow](xa: doobie.Transactor[F]): UserService[F] =
    new UserService[F] {

      import cats.syntax.all._

      def findUser(email: String): OptionT[F, User] =
        OptionT(sql"SELECT * from google_ocdi".query[User].option.transact(xa))

      def insertUser(user: User): F[Unit] =
        sql"INSET INTO google_ocdi(name,email,picture) values (${user.name},${user.email},${user.picture})"
          .update
          .run
          .transact(xa)
          .void
    }
}