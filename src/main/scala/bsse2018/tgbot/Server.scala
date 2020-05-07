package bsse2018.tgbot

import com.bot4s.telegram.api.declarative.Action
import com.bot4s.telegram.models.{Message, User}

import scala.concurrent.{ExecutionContext, Future}

trait Server {
  def registerUser(user: BotUser): Future[Unit]

  def isRegistered(user: User): Future[Boolean]

  def registeredOrNot(ok: Action[Future, User])
                     (noAccess: Action[Future, User])
                     (implicit msg: Message, ec: ExecutionContext): Future[Unit] = {
    msg.from.fold(Future.successful(())) { user =>
      isRegistered(user).flatMap(registered =>
        if (registered)
          ok(user)
        else
          noAccess(user))
    }
  }

  def getAllUsers: Future[Map[Int, String]]

  def sendMessage(toUser: Int, fromUser: User, msg: String): Future[Unit]

  def getNewMessages(user: Int): Future[List[TextMessage]]
}
