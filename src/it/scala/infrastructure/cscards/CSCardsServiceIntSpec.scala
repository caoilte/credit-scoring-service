package infrastructure.cscards

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource}
import cats.implicits._
import domain._
import io.circe.parser._
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import testsupport.WiremockSupport

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class CSCardsServiceIntSpec extends AnyFlatSpec with BeforeAndAfterAll with Matchers with EitherValues with WiremockSupport {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)
  // make trait

  val basePath = s"http://localhost:$wiremockPort"

  val mockCSCardsServer = new MockCSCardsServer(wiremockServer, "")

  behavior of "CS Cards Service"

  it should "work for happy path" in new Scope {
    val expectedRequest = parse(s"""
                                   |{
                                   |  "name": "John Smith",
                                   |  "creditScore": 500
                                   |}
         """.stripMargin).right.value
    mockCSCardsServer.mockCardsResponse(200, expectedRequest, "/cscards/good_response.json")

    withService(service => {
      val result = service
        .makeRecommendations(
          CreditCardApplicant(
            name = Name("John Smith"),
            creditScore = CreditScore(500),
            salary = Salary(18000)
          )
        )
        .unsafeRunSync()
      result should equal(
        List(CreditCardRecommendation(CardProvider("CSCards"),CardName("SuperSaver Card"),EligibilityRating(0.63),APR(21.4)), CreditCardRecommendation(CardProvider("CSCards"),CardName("SuperSpender Card"),EligibilityRating(0.5),APR(19.2)))
      )
      ().pure[F]
    }).unsafeRunSync()
  }

  trait Scope {
    type F[A] = IO[A]

    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val clientR: Resource[F, Client[F]] = AsyncHttpClient.resource[F](clientConfig)

    def withService(testFunc: CSCardsService[F] => F[Unit]): F[Unit] =
      clientR.use(client => {
        testFunc(new CSCardsService[F](client, Uri.unsafeFromString(basePath)))
      })

  }
}
