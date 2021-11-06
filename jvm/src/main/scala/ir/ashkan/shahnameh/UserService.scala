package ir.ashkan.shahnameh

import cats.Monad
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import doobie.Transactor
import cats.syntax.all._
import doobie.implicits._

trait UserService[F[_]] {
  import UserService.User

  def findUser(email: String): OptionT[F, User]

  def insertUser(user: User): F[Unit]

  def findOrInsert(user: GoogleOIDC.User)(implicit M: Monad[F]): F[User] =
    findUser(user.email).getOrElseF {
      val usr = UserService.fromGoogleOCID(user)
      insertUser(usr).as(usr)
    }
}

object UserService {
  case class User(id: Long, name: String, email: String, picture: String)

  def fromGoogleOCID(user: GoogleOIDC.User): User =
    User(0, name = user.name, email = user.email, picture = user.picture)

  def doobie[F[_] : MonadCancelThrow](xa: Transactor[F]): UserService[F] =
    new UserService[F] {
      def findUser(email: String): OptionT[F, User] =
        OptionT(sql"SELECT * from google_ocid where email=$email".query[User].option.transact(xa))

      def insertUser(user: User): F[Unit] =
        sql"INSERT INTO google_ocid(name,email,picture) values (${user.name},${user.email},${user.picture})"
          .update
          .run
          .transact(xa)
          .void
    }
}