package main.scala

import java.lang.{String, StringBuilder}
import java.util.UUID

import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.{Action, Commands}
import com.bot4s.telegram.clients.{FutureSttpClient, ScalajHttpClient}
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.softwaremill.sttp.SttpBackendOptions
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import slogging.{LogLevel, LoggerConfig, PrintLoggerFactory}
import com.bot4s.telegram.models.{Message, User}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.collection.mutable.{ListBuffer, MutableList}
import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}

case class BotUser(id: Int, username: String)

case class TextMessage(
                        fromUser: BotUser,
                        message: String
                      )

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

  def getAllUsers() = {
    listOfUsers
  }

  def sendMessage(toUser: Int, fromUser: User, msg: String): Unit = {
    if (!messagesForUser.contains(toUser))
      messagesForUser += (toUser -> ListBuffer(TextMessage(BotUser(fromUser.id, fromUser.username.getOrElse(fromUser.id.toString)), msg)))
    else
      messagesForUser(toUser) += TextMessage(BotUser(fromUser.id, fromUser.username.getOrElse(fromUser.id.toString)), msg)
  }

  def getNewMessages(user: Int) = {
    val msgs = messagesForUser(user)
    messagesForUser -= user
    msgs
  }

}

class Bot(override val client: RequestHandler[Future]) extends TelegramBot
  with Polling
  with Commands[Future] {

  val server = new Server()

  onCommand("/start") { implicit msg =>
    msg.from match {
      case Some(user) => {
        server.registerUser(user.id, user.username.getOrElse(user.id.toString))
        reply(s"You've been successfully registered! ${user.id}").void
      }
      case None => Future.unit
    }
  }

  onCommand("/users") { implicit msg =>
    reply(server.getAllUsers().values.mkString("\n")).void
  }

  onCommand("/send") { implicit msg =>
    server.registeredOrNot { admin =>
      withArgs { args =>
        if (args.length != 2)
          reply("You should provide 2 arguments").void
        else {
          try {
            server.sendMessage(args(0).toInt, admin, args(1).mkString)
          } catch {
            case _: NumberFormatException => reply("First argument should be an integer").void
            case _: Throwable => reply("Something went wrong").void
          }
          reply("Message has been sent!").void
        }
      }
    } /* or else */ {
      user =>
        reply(s"${user.firstName}, you must /start first.").void
    }
  }

  onCommand("/check") { implicit msg =>
    server.registeredOrNot { admin =>
      reply(server.getNewMessages(admin.id).map(msg => s"${msg.fromUser.username}: ${msg.message}").mkString("\n")).void
    } /* or else */ {
      user =>
        reply(s"${user.firstName}, you must /start first.").void
    }
  }

}

object BotStarter {
  def main(args: Array[String]): Unit = {
    implicit val ec = ExecutionContext.global
    implicit val backend = OkHttpFutureBackend(
      SttpBackendOptions.Default.socksProxy("ps8yglk.ddns.net", 11999)
    )

    // TODO: move token to the FILE and make it secured!!!
    val token = ""
    val bot = new Bot(new FutureSttpClient((token)))
    Await.result(bot.run(), Duration.Inf)
  }
}