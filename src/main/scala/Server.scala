import com.bot4s.telegram.models.User

import com.bot4s.telegram.api.declarative.Action
import com.bot4s.telegram.models.{Message, User}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait Server {
  def registerUser(user: BotUser): Unit

  def isRegistered(user: User): Boolean

  def registeredOrNot(ok: Action[Future, User])
                     (noAccess: Action[Future, User])
                     (implicit msg: Message): Future[Unit]

  def getAllUsers: mutable.HashMap[Int, String]
  def sendMessage(toUser: Int, fromUser: User, msg: String): Unit

  def getNewMessages(user: Int): ListBuffer[TextMessage]
}
