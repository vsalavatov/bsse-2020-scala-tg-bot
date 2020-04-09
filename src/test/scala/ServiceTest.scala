import com.bot4s.telegram.models.User
import com.softwaremill.sttp.SttpBackend
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import random.Randomizer

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object RandomMock extends Randomizer {
  override def randomElem[T](list: List[T]) : Option[T] = list.headOption
}

class ServiceTest extends AnyFlatSpec with Matchers with MockFactory {

  trait mocks {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    implicit val backend: SttpBackend[Future, Nothing] = mock[SttpBackend[Future, Nothing]]
    implicit val randomizer: RandomMock.type = RandomMock
    val service = new PictureService("", randomizer)
  }

  "PictureService" should "throw NoCatException" in new mocks {
    (backend.send[String] _).expects(*).returning(Future.successful(
      com.softwaremill.sttp.Response.error("No cats found", 404)
    ))

    ScalaFutures.whenReady(service.getImage("cat").failed) { e =>
      e shouldBe an[NoSuchElementException]
    }
  }


  "PictureService" should "return single cat" in new mocks {
    (backend.send[Response] _).expects(*).returning(Future.successful(
      com.softwaremill.sttp.Response.ok(Response(List(Data(images = List(InnerData("solo cat"))))))
    ))

    ScalaFutures.whenReady(service.getImage("cat")) { cat =>
      cat shouldBe "solo cat"
    }
  }


  "PictureService" should "return first cat" in new mocks {
    (backend.send[Response] _).expects(*).returning(Future.successful(
      com.softwaremill.sttp.Response.ok(Response(List(Data(images = List(
        InnerData("uno cat"),
        InnerData("dos cat"),
        InnerData("cuatro cat"))))))
    ))

    ScalaFutures.whenReady(service.getImage("cat")) { cat =>
      cat shouldBe "uno cat"
    }
  }
}

class ServerTest extends AnyFlatSpec with Matchers {
  trait mock {
    val server = new ServerInMemory()
    val users: List[BotUser] = List(
      BotUser(1, "Sonya"), BotUser(2, "Ilyich"),
      BotUser(4, "Yura"), BotUser(5, "Puhlyash")
    )
  }

  "Server" should "return all registered users" in new mock {
    users.foreach(user => server.registerUser(user))
    server.getAllUsers shouldBe users.map(user => user.id -> user.username).toMap
  }

  "Server" should "send messages and clear them" in new mock {
    server.sendMessage(2, User(1, isBot = false, firstName = "Sonya", username = Some("Sonya")), "uno uno uno")
    server.getNewMessages(2) shouldBe ListBuffer(TextMessage(BotUser(1, "Sonya"),"uno uno uno"))
    server.getNewMessages(2) shouldBe ListBuffer()
  }
}
