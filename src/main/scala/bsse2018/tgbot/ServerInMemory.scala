package bsse2018.tgbot

import com.bot4s.telegram.models.User

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ServerInMemory(implicit val pictureService: PictureService,  implicit val ec: ExecutionContext) extends Server {
  private var listOfUsers = new mutable.HashMap[Int, String]
  private var messagesForUser = new mutable.HashMap[Int, ListBuffer[TextMessage]]
  private var sendImages = new ListBuffer[(Int, String)]

  override def registerUser(user: BotUser): Unit = {
    listOfUsers += (user.id -> user.username)
  }

  override def isRegistered(user: User): Boolean = {
    listOfUsers.contains(user.id)
  }

  override def getAllUsers: Map[Int, String] = listOfUsers.toMap

  override def sendMessage(toUser: Int, fromUser: User, msg: String): Unit = {
    messagesForUser.getOrElseUpdate(toUser, ListBuffer()) +=
      TextMessage(BotUser(fromUser.id, fromUser.username.getOrElse(fromUser.id.toString)), msg)
  }

  override def getNewMessages(user: Int): List[TextMessage] = {
    val msgs = messagesForUser.getOrElse(user, ListBuffer())
    messagesForUser -= user
    msgs.toList
  }

  override def getImage(tag: String, userId: Option[Int]): Future[String] = {
    pictureService.getImage(tag).flatMap { img =>
      userId match {
        case Some(id) => sendImages += {(id, img)}
      }
      Future.successful(img)
    }
  }

  override def getStats(idOrLogin: String): Future[Option[String]] = {
    val filterCriteria = Try(idOrLogin.toInt).toOption
    val realId = listOfUsers.filter { p =>
        filterCriteria match {
          case None => p._2 == idOrLogin
          case Some(id) => p._1 == id
        }
      }.keys.headOption
    realId match {
      case None => Future.successful(None)
      case Some(id) => Future.successful(Try(sendImages.filter(_._1 == id).map(_._2).result.mkString("\n")).toOption)
    }
  }
}
