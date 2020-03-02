import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.{Action, Commands}
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.models.{Message, User}
import com.softwaremill.sttp.{SttpBackend, SttpBackendOptions, sttp}
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import org.json4s.native.Serialization
import com.softwaremill.sttp.json4s._
import com.softwaremill.sttp._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random;

case class BotUser(id: Int, username: String)

case class TextMessage(
                        fromUser: BotUser,
                        message: String
                      )

case class Response(data: List[Data])
case class Data(images: List[InnerData])
case class InnerData(link: String)

class PictureService(implicit val backend: SttpBackend[Future, Nothing], implicit val ec: ExecutionContext) {

  private implicit val serialization: Serialization.type = org.json4s.native.Serialization

  def getImage(tag: String): Future[String] = {
    val request = sttp
      // TODO: Move Client-ID to the file and make it secured
      .header("Authorization", "Client-ID XXX")
      .get(uri"https://api.imgur.com/3/gallery/search?q=${tag}")
      .response(asJson[Response])

    backend.send(request).map { response =>
      // TODO: what if there's no head
      Random.shuffle(response.unsafeBody.data.flatMap(_.images)).head.link
    }
  }
}

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

class Bot(override val client: RequestHandler[Future], val server: Server, val service: PictureService) extends TelegramBot
  with Polling
  with Commands[Future] {


  onCommand("/start") { implicit msg =>
    msg.from match {
      case Some(user) => {
        server.registerUser(user.id, user.username.getOrElse(user.id.toString))
        reply(s"You've been successfully registered! Your id: ${user.id}").void
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
        //TODO: I don't like this magic constant
        if (args.length != 2)
          reply("You should provide 2 arguments").void
        else {
          try {
            server.sendMessage(args(0).toInt, admin, args(1).mkString)
          } catch {
            case _: NumberFormatException => reply("First argument should be an integer. Usage: /send id message").void
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

  onCommand("/img") { implicit msg =>
    server.registeredOrNot { admin =>
      withArgs { args =>
        try {
          if (args.isEmpty) throw new IndexOutOfBoundsException()
          // TODO: what if getImage is empty
          service.getImage(args.mkString(" ")).flatMap { link =>
            reply(link)
          }.void
        } catch {
          case _: IndexOutOfBoundsException => reply("Empty argument list. Usage: /img tag").void
        }
      }
    } /* or else */ {
      user =>
        reply(s"${user.firstName}, you must /start first.").void
    }
  }

}

object BotStarter {
  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val backend: SttpBackend[Future, Nothing] = OkHttpFutureBackend(
      SttpBackendOptions.Default.socksProxy("ps8yglk.ddns.net", 11999)
    )

    // TODO: move token to the FILE and make it secured!!!
    val token = ""
    val server = new Server()
    val service = new PictureService()
    val bot = new Bot(new FutureSttpClient((token)), server, service)
    Await.result(bot.run(), Duration.Inf)
  }
}