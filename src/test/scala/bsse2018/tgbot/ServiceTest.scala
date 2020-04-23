package bsse2018.tgbot

import bsse2018.tgbot.random.Randomizer
import com.bot4s.telegram.models.User
import com.softwaremill.sttp.{SttpBackend, SttpBackendOptions}
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import slick.jdbc.H2Profile.api._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

object RandomMock extends Randomizer {
  override def randomElem[T](list: List[T]): Option[T] = list.headOption
}

class ServiceTest extends AnyFlatSpec with Matchers with MockFactory {

  trait mocks {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    implicit val backend: SttpBackend[Future, Nothing] = mock[SttpBackend[Future, Nothing]]
    implicit val randomizer: RandomMock.type = RandomMock
    val service = new PictureService("", randomizer)
  }

  "bsse2018.tgbot.PictureService" should "throw NoCatException" in new mocks {
    (backend.send[String] _).expects(*).returning(Future.successful(
      com.softwaremill.sttp.Response.error("No cats found", 404)
    ))

    ScalaFutures.whenReady(service.getImage("cat").failed) { e =>
      e shouldBe an[NoSuchElementException]
    }
  }


  "bsse2018.tgbot.PictureService" should "return single cat" in new mocks {
    (backend.send[Response] _).expects(*).returning(Future.successful(
      com.softwaremill.sttp.Response.ok(Response(List(Data(images = List(InnerData("solo cat"))))))
    ))

    ScalaFutures.whenReady(service.getImage("cat")) { cat =>
      cat shouldBe "solo cat"
    }
  }


  "bsse2018.tgbot.PictureService" should "return first cat" in new mocks {
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


class ServerInMemoryTest extends AnyFlatSpec with Matchers with MockFactory {

  trait mocks {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val backend = mock[SttpBackend[Future, Nothing]]
    implicit val pictureService: PictureService = new PictureService("")
    val server = new ServerInMemory()
    val users: List[BotUser] = List(
      BotUser(1, "Sonya"), BotUser(2, "Ilyich"),
      BotUser(4, "Yura"), BotUser(5, "Puhlyash")
    )
  }

  "ServerInMemory" should "return all registered users" in new mocks {
    users.foreach(user => Await.result(server.registerUser(user), Duration.Inf))
    Await.result(server.getAllUsers, Duration.Inf) shouldBe users.map(user => user.id -> user.username).toMap
  }


  "ServerInMemory" should "send messages and clear them" in new mocks {
    Await.result(server.sendMessage(2, User(1, isBot = false, firstName = "Sonya", username = Some("Sonya")), "uno uno uno"), Duration.Inf)
    Await.result(server.getNewMessages(2), Duration.Inf) shouldBe ListBuffer(TextMessage(BotUser(1, "Sonya"), "uno uno uno"))
    Await.result(server.getNewMessages(2), Duration.Inf) shouldBe ListBuffer()
  }
}

class ServerDBTest extends AnyFlatSpec with Matchers with MockFactory {

  trait mocks {
    val db = Database.forURL("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    implicit val ec = ExecutionContext.global
    implicit val backend = mock[SttpBackend[Future, Nothing]]
    implicit val pictureService: PictureService = new PictureService("")
    val server = new ServerDB(db)
    val users: List[BotUser] = List(
      BotUser(1, "Sonya"), BotUser(2, "Ilyich"),
      BotUser(4, "Yura"), BotUser(5, "Puhlyash")
    )
  }

  "ServerDB" should "return all registered users" in new mocks {
    users.foreach(user => Await.result(server.registerUser(user), Duration.Inf))
    Await.result(server.getAllUsers, Duration.Inf) shouldBe users.map(user => user.id -> user.username).toMap

    db.close()
  }

  "ServerDB" should "send messages and clear them" in new mocks {
    Await.result(server.sendMessage(2, User(1, isBot = false, firstName = "Sonya", username = Some("Sonya")), "uno uno uno"), Duration.Inf)
    Await.result(server.getNewMessages(2), Duration.Inf) shouldBe ListBuffer(TextMessage(BotUser(1, "Sonya"), "uno uno uno"))
    Await.result(server.getNewMessages(2), Duration.Inf) shouldBe ListBuffer()

    db.close()
  }
}
