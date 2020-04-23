import org.json4s.native.Serialization
import com.softwaremill.sttp.json4s._
import com.softwaremill.sttp._
import com.softwaremill.sttp.sttp
import random.{RandomRest, Randomizer}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class PictureService(val imgurClientId: String, val random: Randomizer = RandomRest)(implicit val backend: SttpBackend[Future, Nothing], implicit val ec: ExecutionContext) {

  private implicit val serialization: Serialization.type = org.json4s.native.Serialization

  def getImage(tag: String): Future[String] = {
    val request = sttp
      .header("Authorization", "Client-ID " + imgurClientId)
      .get(uri"https://api.imgur.com/3/gallery/search?q=${tag}")
      .response(asJson[Response])


    backend.send(request).flatMap { response =>
      val images = response.unsafeBody.data.flatMap(_.images)
      random.randomElem(images) match {
        case Some(img) => Future.successful(img.link)
        case None => Future.failed(NoImageException("Sorry, no images have been found..."))
      }
    }
  }
}

case class NoImageException(msg: String) extends Exception
