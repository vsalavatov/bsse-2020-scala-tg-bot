package bsse2018.tgbot

import com.bot4s.telegram.models.User

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class ServerInMemory(implicit ec: ExecutionContext) extends Server {
  private var listOfUsers = new mutable.HashMap[Int, String]
  private var messagesForUser = new mutable.HashMap[Int, ListBuffer[TextMessage]]

  override def registerUser(user: BotUser): Future[Unit] = Future {
    listOfUsers += (user.id -> user.username)
  }

  override def isRegistered(user: User): Future[Boolean] = Future {
    listOfUsers.contains(user.id)
  }

  override def getAllUsers: Future[Map[Int, String]] = Future {
    listOfUsers.toMap
  }

  override def sendMessage(toUser: Int, fromUser: User, msg: String): Future[Unit] = Future {
    messagesForUser.getOrElseUpdate(toUser, ListBuffer()) +=
      TextMessage(BotUser(fromUser.id, fromUser.username.getOrElse(fromUser.id.toString)), msg)
  }

  override def getNewMessages(user: Int): Future[List[TextMessage]] = Future {
    val msgs = messagesForUser.getOrElse(user, ListBuffer())
    messagesForUser -= user
    msgs.toList
  }
}
