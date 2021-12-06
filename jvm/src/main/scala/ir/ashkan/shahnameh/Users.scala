package ir.ashkan.shahnameh

import cats.Monad
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import doobie.{Get, Put, Transactor}
import cats.syntax.all._
import doobie.implicits._
import Sessions.SessionId
import simulacrum._

@typeclass
trait Users[F[_]] {

  import Users.{User, UserId}

  def find(email: String): OptionT[F, User]

  def findLoggedIn(sessionId: SessionId): OptionT[F, User]

  def insert(user: User): F[Unit]

  def login(id: UserId, sessionId: SessionId): F[Unit]

  def logout(id: UserId): F[Unit]

  def findOrInsert(user: GoogleOIDC.User)(implicit M: Monad[F]): F[User] =
    find(user.email).getOrElseF {
      val usr = Users.fromGoogle(user)
      insert(usr).as(usr)
    }
}

object Users {
  type UserId = Long

  case class User(id: UserId, name: String, email: String, picture: String, sessionId: Option[SessionId])

  def fromGoogle(user: GoogleOIDC.User): User =
    User(0, name = user.name, email = user.email, picture = user.picture, None)

  def doobie[F[_] : MonadCancelThrow](xa: Transactor[F]): Users[F] =
    new Users[F] {
      implicit val putSessionId: Put[SessionId] = ???
      implicit val getUser: Get[User] = ???

      def find(email: String): OptionT[F, User] =
        OptionT(
          sql"SELECT * from google_ocid where email=$email"
            .query[User]
            .option
            .transact(xa)
        )

      def insert(user: User): F[Unit] =
        sql"INSERT INTO google_ocid(name,email,picture) values (${user.name},${user.email},${user.picture})"
          .update
          .run
          .transact(xa)
          .void

      def logout(id: UserId): F[Unit] =
        sql"UPDATE google_ocid SET session_id = null WHERE id=$id"
          .update
          .run
          .transact(xa)
          .void

      def findLoggedIn(sessionId: SessionId): OptionT[F, User] =
        OptionT(
          sql"SELECT * from google_ocid where session_id=$sessionId"
            .query[User]
            .option
            .transact(xa)
        )

      def login(id: UserId, sessionId: SessionId): F[Unit] =
        sql"UPDATE google_ocid SET session_id = $sessionId"
          .update
          .run
          .transact(xa)
          .void
    }
}