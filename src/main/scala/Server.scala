import com.bot4s.telegram.api.declarative.Action
import com.bot4s.telegram.models.{Message, User}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class Server {
  private var listOfUsers = new mutable.HashMap[Int, String]
  private var messagesForUser = new mutable.HashMap[Int, ListBuffer[TextMessage]]

  def registerUser(id: Int, username: String): Unit = {
    listOfUsers += (id -> username)
  }

  def isRegistered(user: User): Boolean = {
    listOfUsers.contains(user.id)
  }

  def registeredOrNot(ok: Action[Future, User])
                     (noAccess: Action[Future, User])
                     (implicit msg: Message): Future[Unit] = {
    msg.from.fold(Future.successful(())) { user =>
      if (isRegistered(user))
        ok(user)
      else
        noAccess(user)
    }
  }

  def getAllUsers() = listOfUsers

  def sendMessage(toUser: Int, fromUser: User, msg: String): Unit = {
    messagesForUser.getOrElseUpdate(toUser, ListBuffer()) +=
      TextMessage(BotUser(fromUser.id, fromUser.username.getOrElse(fromUser.id.toString)), msg)
  }

  def getNewMessages(user: Int) = {
    val msgs = messagesForUser(user)
    messagesForUser -= user
    msgs
  }
}
