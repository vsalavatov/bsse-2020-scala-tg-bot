import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.models.InputFile
import com.softwaremill.sttp.{SttpBackend, SttpBackendOptions}
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

case class BotUser(id: Int, username: String)

case class TextMessage(
                        fromUser: BotUser,
                        message: String
                      )

case class Response(data: List[Data])

case class Data(images: List[InnerData])

case class InnerData(link: String)

class Bot(override val client: RequestHandler[Future], val server: Server, val service: PictureService) extends TelegramBot
  with Polling
  with Commands[Future]
  with PhotoTrait[Future] {

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
        if (args.length < 2)
          reply("Usage: /send id message").void
        else {
          try {
            server.sendMessage(args(0).toInt, admin, msg.text.get.dropWhile(_ != ' ').dropWhile(_ != ' '))
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
      val tag = msg.text.get.drop("/img ".length)
      try {
        if (tag.isEmpty) throw new IndexOutOfBoundsException()
        service.getImage(tag).flatMap { link =>
          try {
            replyWithPhoto(InputFile(link))
          } catch {
            case _ => reply(link) // maybe it isn't a photo...
          }
        }.void
      } catch {
        case e: NoImageException => reply(e.msg).void
        case _: IndexOutOfBoundsException => reply("Empty argument list. Usage: /img tag").void
      }
    } /* or else */ {
      user =>
        reply(s"${user.firstName}, you must /start first.").void
    }
  }
}

object BotStarter {
  def main(args: Array[String]): Unit = {
    case class Config(telegramToken: String = "", imgurClientId: String = "")
    val parser = new scopt.OptionParser[Config]("bot") {
      head("telegram bot", "0.1")

      opt[String]('t', "token")
        .required()
        .valueName("<telegram bot token>")
        .action((x, c) => c.copy(telegramToken = x))

      opt[String]('i', "imgur")
        .required()
        .valueName("<imgur client id>")
        .action((x, c) => c.copy(imgurClientId = x))
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        implicit val ec: ExecutionContext = ExecutionContext.global
        implicit val backend: SttpBackend[Future, Nothing] = OkHttpFutureBackend(
          SttpBackendOptions.Default.socksProxy("ps8yglk.ddns.net", 11999)
        )
        val server = new Server()
        val service = new PictureService(config.imgurClientId)
        val bot = new Bot(new FutureSttpClient((config.telegramToken)), server, service)
        Await.result(bot.run(), Duration.Inf)
      case None =>
        println("You must specify service tokens!")
    }
  }
}