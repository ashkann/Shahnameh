package ir.ashkan.shahnameh

import cats.Monad
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import doobie.Transactor
import cats.syntax.all._
import doobie.implicits._
import Session.SessionId
import simulacrum.typeclass

@typeclass
trait UserService[F[_]] {
  import UserService.{User, UserId}

  def findUser(email: String): OptionT[F, User]

  def insertUser(user: User): F[Unit]

  def login(userId: UserId): F[SessionId]

  def logout(userId: UserId): F[Unit]

  def findOrInsert(user: GoogleOIDC.User)(implicit M: Monad[F]): F[User] =
    findUser(user.email).getOrElseF {
      val usr = UserService.fromGoogleOCID(user)
      insertUser(usr).as(usr)
    }
}

object UserService {
  type UserId = Long

  case class User(id: UserId, name: String, email: String, picture: String, sessionId: Option[SessionId])

  def fromGoogleOCID(user: GoogleOIDC.User): User =
    User(0, name = user.name, email = user.email, picture = user.picture, None)

  def doobie[F[_] : MonadCancelThrow : Session](xa: Transactor[F]): UserService[F] =
    new UserService[F] {
      def findUser(email: String): OptionT[F, User] =
        OptionT(sql"SELECT * from google_ocid where email=$email".query[User].option.transact(xa))

      def insertUser(user: User): F[Unit] =
        sql"INSERT INTO google_ocid(name,email,picture) values (${user.name},${user.email},${user.picture})"
          .update
          .run
          .transact(xa)
          .void

      def login(userId: Long): F[SessionId] =
        for {
          sessionId <- Session[F].generateId
          _ <- sql"UPDATE google_ocid SET session_id = $sessionId"
            .update
            .run
            .transact(xa)
        } yield sessionId

      def logout(userId: UserId): F[Unit] =
        sql"UPDATE google_ocid SET session_id = null"
        .update
        .run
        .transact(xa)
        .void
    }
}