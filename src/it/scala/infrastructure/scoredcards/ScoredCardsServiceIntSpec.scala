package infrastructure.scoredcards

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource}
import domain.{APR, CardName, CardProvider, CreditCardApplicant, CreditCardRecommendation, CreditScore, EligibilityRating, Name, Salary}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import testsupport.WiremockSupport
import io.circe.parser._
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import cats.implicits._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class ScoredCardsServiceIntSpec extends AnyFlatSpec with BeforeAndAfterAll with Matchers with EitherValues with WiremockSupport {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)
  // make trait

  val basePath = s"http://localhost:$wiremockPort"

  val mockScoredCardsServer = new MockScoredCardsServer(wiremockServer, "")

  behavior of "Scored Cards Service"

  it should "work for happy path" in new Scope {
    val expectedRequest = parse(s"""
                                   |{
                                   |  "name": "John Smith",
                                   |  "creditScore": 500,
                                   |  "salary": 18000
                                   |}
         """.stripMargin).right.value
    mockScoredCardsServer.mockCardsResponse(200, expectedRequest, "/scoredcards/good_response.json")

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
        List(CreditCardRecommendation(CardProvider("ScoredCards"), CardName("ScoredCard Builder"), EligibilityRating(0.800), APR(19.4)))
      )
      ().pure[F]
    }).unsafeRunSync()
  }

  trait Scope {
    type F[A] = IO[A]

    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val clientR: Resource[F, Client[F]] = AsyncHttpClient.resource[F](clientConfig)

    def withService(testFunc: ScoredCardsService[F] => F[Unit]): F[Unit] =
      clientR.use(client => {
        testFunc(new ScoredCardsService[F](client, Uri.unsafeFromString(basePath)))
      })

  }
}
