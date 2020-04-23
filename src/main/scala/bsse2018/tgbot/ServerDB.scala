package bsse2018.tgbot

import com.bot4s.telegram.models.User
import slick.jdbc.H2Profile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success

class ServerDB(val db: Database)(implicit ec: ExecutionContext) extends Server {

  private class RegisteredUsers(tag: Tag) extends Table[(Int, String)](tag, "REGISTERED_USERS") {
    def userId = column[Int]("USER_ID", O.PrimaryKey)

    def username = column[String]("USERNAME")

    def * = (userId, username)
  }

  private class PostponedMessages(tag: Tag) extends Table[(Int, Int, String, String)](tag, "POSTPONED_MESSAGES") {
    def toUserId = column[Int]("TO_USER_ID")

    def fromUserId = column[Int]("FROM_USER_ID")

    def fromUsername = column[String]("FROM_USERNAME")

    def message = column[String]("MESSAGE")

    def * = (toUserId, fromUserId, fromUsername, message)
  }

  private val registeredUsers = TableQuery[RegisteredUsers]
  private val postponedMessages = TableQuery[PostponedMessages]

  private val initQuery = for {
    _ <- db.run(registeredUsers.schema.createIfNotExists)
    _ <- db.run(postponedMessages.schema.createIfNotExists)
  } yield ()

  override def registerUser(user: BotUser): Future[Unit] = initQuery.flatMap { _ =>
    val query = for {
      _ <- registeredUsers += (user.id, user.username)
    } yield ()
    db.run(query)
  }

  override def isRegistered(user: User): Future[Boolean] = initQuery.flatMap { _ =>
    val query = registeredUsers.filter(_.userId === user.id).result
    db.run(query).flatMap(r => Future {
      r.nonEmpty
    })
  }

  override def getAllUsers: Future[Map[Int, String]] = initQuery.flatMap { _ =>
    val query = registeredUsers.result
    db.run(query).flatMap(r => Future {
      r.toMap
    })
  }

  override def sendMessage(toUser: Int, fromUser: User, msg: String): Future[Unit] = initQuery.flatMap { _ =>
    val query = for {
      _ <- postponedMessages += (toUser, fromUser.id, fromUser.username.getOrElse(fromUser.id.toString), msg)
    } yield ()
    db.run(query)
  }

  override def getNewMessages(user: Int): Future[List[TextMessage]] = initQuery.flatMap { _ =>
    val messagesQuery = postponedMessages.filter(_.toUserId === user)
    db.run(messagesQuery.result).flatMap(messages => Future {
      db.run(messagesQuery.delete)
      messages.map {
        case (_, fromUserId, fromUsername, msg) => TextMessage(BotUser(fromUserId, fromUsername), msg)
      }.toList
    })
  }
}
