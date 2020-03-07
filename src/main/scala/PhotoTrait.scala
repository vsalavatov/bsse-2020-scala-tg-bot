import com.bot4s.telegram.api.declarative.Messages
import com.bot4s.telegram.methods.ParseMode.ParseMode
import com.bot4s.telegram.methods.SendPhoto
import com.bot4s.telegram.models.{InputFile, Message, ReplyMarkup}

import scala.concurrent.Future

trait PhotoTrait[F[_]] extends Messages[F] {
  def replyWithPhoto(photo               : InputFile,
                     caption             : Option[String] = None,
                     parseMode           : Option[ParseMode] = None,
                     disableNotification : Option[Boolean] = None,
                     replyToMessageId    : Option[Int] = None,
                     replyMarkup         : Option[ReplyMarkup] = None)
                    (implicit msg: Message): F[Message] = {
    request(SendPhoto(msg.source, photo, caption, parseMode, disableNotification, replyToMessageId, replyMarkup))
  }
}
