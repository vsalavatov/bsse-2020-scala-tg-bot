package bsse2018.tgbot

import com.bot4s.telegram.models.User
import slick.jdbc.H2Profile.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class ServerDB(val db: Database)(implicit val pictureService: PictureService, implicit val ec: ExecutionContext) extends Server {

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

  private class SendImages(tag: Tag) extends Table[(Long, Int, String)](tag, "SEND_IMAGES") {
    def id = column[Long]("ID", O.AutoInc, O.PrimaryKey)

    def userId = column[Int]("USER_ID")

    def link = column[String]("IMAGE_LINKS")

    def * = (id, userId, link)
  }

  private val registeredUsers = TableQuery[RegisteredUsers]
  private val postponedMessages = TableQuery[PostponedMessages]
  private val sendImages = TableQuery[SendImages]

  private val initQuery = for {
    _ <- db.run(registeredUsers.schema.createIfNotExists)
    _ <- db.run(postponedMessages.schema.createIfNotExists)
    _ <- db.run(sendImages.schema.createIfNotExists)
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

  override def getImage(tag: String, userId: Option[Int]): Future[String] = initQuery.flatMap { _ =>
    pictureService.getImage(tag).flatMap { img =>
      userId match {
        case Some(id) => db.run(sendImages += (-1, id, img))
      }
      Future.successful(img)
    }
  }

  override def getStats(idOrLogin: String): Future[Option[String]] = initQuery.flatMap { _ =>
    val filterCriteria: Option[Int] = Try(idOrLogin.toInt).toOption
    val transaction = for {
      realId <- registeredUsers.filter { user: RegisteredUsers =>
        user.userId.? === filterCriteria || user.username === idOrLogin
      }.map(_.userId).result.headOption
      images <- sendImages.filter(_.userId.? === realId).map(_.link).result
    } yield realId.map(_ => images.toList)
    db.run(transaction).map(_.map(img => img.mkString("\n")))
  }
}
