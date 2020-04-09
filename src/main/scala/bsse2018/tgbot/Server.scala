package bsse2018.tgbot

import com.bot4s.telegram.api.declarative.Action
import com.bot4s.telegram.models.{Message, User}

import scala.concurrent.Future

trait Server {
  def registerUser(user: BotUser): Unit

  def isRegistered(user: User): Boolean

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

  def getAllUsers: Map[Int, String]
  def sendMessage(toUser: Int, fromUser: User, msg: String): Unit

  def getNewMessages(user: Int): List[TextMessage]
}
