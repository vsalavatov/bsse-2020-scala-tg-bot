import com.bot4s.telegram.api.declarative.Action
import com.bot4s.telegram.models.{Message, User}
import com.softwaremill.sttp.SttpBackend

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class ServerInMemory extends Server {
  private var listOfUsers = new mutable.HashMap[Int, String]
  private var messagesForUser = new mutable.HashMap[Int, ListBuffer[TextMessage]]

  override def registerUser(user: BotUser): Unit = {
    listOfUsers += (user.id -> user.username)
  }

  override def isRegistered(user: User): Boolean = {
    listOfUsers.contains(user.id)
  }

  override def registeredOrNot(ok: Action[Future, User])
                     (noAccess: Action[Future, User])
                     (implicit msg: Message): Future[Unit] = {
    msg.from.fold(Future.successful(())) { user =>
      if (isRegistered(user))
        ok(user)
      else
        noAccess(user)
    }
  }

  override def getAllUsers: mutable.HashMap[Int, String] = listOfUsers

  override def sendMessage(toUser: Int, fromUser: User, msg: String): Unit = {
    messagesForUser.getOrElseUpdate(toUser, ListBuffer()) +=
      TextMessage(BotUser(fromUser.id, fromUser.username.getOrElse(fromUser.id.toString)), msg)
  }

  override def getNewMessages(user: Int): ListBuffer[TextMessage] = {
    val msgs = messagesForUser.getOrElse(user, ListBuffer())
    messagesForUser -= user
    msgs
  }
}
