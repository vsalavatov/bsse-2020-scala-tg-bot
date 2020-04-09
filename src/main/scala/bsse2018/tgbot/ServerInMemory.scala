package bsse2018.tgbot

import com.bot4s.telegram.models.User

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class ServerInMemory extends Server {
  private var listOfUsers = new mutable.HashMap[Int, String]
  private var messagesForUser = new mutable.HashMap[Int, ListBuffer[TextMessage]]

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
}
