package server

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO, Resource, Timer}
import infrastructure.MultiPartnerCreditCardRecommendationService
import infrastructure.api.{CreditCardScoringApi, CreditCardScoringRestService}
import infrastructure.cscards.{CSCardsService, MockCSCardsServer}
import infrastructure.scoredcards.{MockScoredCardsServer, ScoredCardsService}
import io.circe.Json
import io.circe.parser.parse
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import testsupport.WiremockSupport

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class EndToEndSpec extends AnyFlatSpec with BeforeAndAfterAll with Matchers with EitherValues with WiremockSupport {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)

  implicit val timer: Timer[IO] = IO.timer(ec)
  // make trait

  val basePath = s"http://localhost:$wiremockPort"
  val csCardsSuffix = "/cscards"
  val scoredCardsSuffix = "/scored"
  val csCardsBasePath = s"$basePath$csCardsSuffix"
  val scoredCardsBasePath = s"$basePath$scoredCardsSuffix"
  val mockCSCardsServer = new MockCSCardsServer(wiremockServer, csCardsSuffix)
  val mockScoredCardsServer = new MockScoredCardsServer(wiremockServer, scoredCardsSuffix)


  behavior of "Credit Card Scoring Service"

  it should "return correctly sorted cards recommended by both services" in new Scope {
    withRoutes(
      routes => {

        val expectedScoredCardsRequest = parse(s"""
                                       |{
                                       |  "name": "John Smith",
                                       |  "score": 500,
                                       |  "salary": 18000
                                       |}
         """.stripMargin).right.value
        mockScoredCardsServer.mockCardsResponse(200, expectedScoredCardsRequest, "/scoredcards/good_response.json")

        val expectedCSCardsRequest = parse(s"""
                                       |{
                                       |  "name": "John Smith",
                                       |  "creditScore": 500
                                       |}
         """.stripMargin).right.value
        mockCSCardsServer.mockCardsResponse(200, expectedCSCardsRequest, "/cscards/good_response.json")


        val requestEntity = parse(s"""
                                                  |{
                                                  |  "name": "John Smith",
                                                  |  "creditScore": 500,
                                                  |  "salary": 18000
                                                  |}
         """.stripMargin).right.value

        val expectedResponse = parse(s"""
                                     |[
                                     |  {
                                     |    "provider" : "CSCards",
                                     |    "name" : "SuperSpender Card",
                                     |    "apr" : 19.2,
                                     |    "cardScore" : 0.5
                                     |  },
                                     |  {
                                     |    "provider" : "CSCards",
                                     |    "name" : "SuperSaver Card",
                                     |    "apr" : 21.4,
                                     |    "cardScore" : 0.63
                                     |  },
                                     |  {
                                     |    "provider" : "ScoredCards",
                                     |    "name" : "ScoredCard Builder",
                                     |    "apr" : 19.4,
                                     |    "cardScore" : 0.800
                                     |  }
                                     |]
         """.stripMargin).right.value


        val api = routes.orNotFound
        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"creditcards"))
          response <- api.run(request)
          _ = response.status.code should === (200)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (expectedResponse)
        } yield ()
      }
    ).unsafeRunSync()
  }

  it should "return only CSCards if ScoredCards returns an error" in new Scope {
    withRoutes(
      routes => {

        val expectedScoredCardsRequest = parse(s"""
                                                  |{
                                                  |  "name": "John Smith",
                                                  |  "score": 500,
                                                  |  "salary": 18000
                                                  |}
         """.stripMargin).right.value
        mockScoredCardsServer.mockCardsResponse(500, expectedScoredCardsRequest, "")

        val expectedCSCardsRequest = parse(s"""
                                              |{
                                              |  "name": "John Smith",
                                              |  "creditScore": 500
                                              |}
         """.stripMargin).right.value
        mockCSCardsServer.mockCardsResponse(200, expectedCSCardsRequest, "/cscards/good_response.json")


        val requestEntity = parse(s"""
                                     |{
                                     |  "name": "John Smith",
                                     |  "creditScore": 500,
                                     |  "salary": 18000
                                     |}
         """.stripMargin).right.value

        val expectedResponse = parse(s"""
                                        |[
                                        |  {
                                        |    "provider" : "CSCards",
                                        |    "name" : "SuperSpender Card",
                                        |    "apr" : 19.2,
                                        |    "cardScore" : 0.5
                                        |  },
                                        |  {
                                        |    "provider" : "CSCards",
                                        |    "name" : "SuperSaver Card",
                                        |    "apr" : 21.4,
                                        |    "cardScore" : 0.63
                                        |  }
                                        |]
         """.stripMargin).right.value


        val api = routes.orNotFound
        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"creditcards"))
          response <- api.run(request)
          _ = response.status.code should === (200)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (expectedResponse)
        } yield ()
      }
    ).unsafeRunSync()
  }

  it should "return only ScoredCards if CSCards takes a long time to responsd" in new Scope {
    withRoutes(
      routes => {

        val expectedScoredCardsRequest = parse(s"""
                                                  |{
                                                  |  "name": "John Smith",
                                                  |  "score": 500,
                                                  |  "salary": 18000
                                                  |}
         """.stripMargin).right.value
        mockScoredCardsServer.mockCardsResponse(200, expectedScoredCardsRequest, "/scoredcards/good_response.json")

        val expectedCSCardsRequest = parse(s"""
                                              |{
                                              |  "name": "John Smith",
                                              |  "creditScore": 500
                                              |}
         """.stripMargin).right.value
        mockCSCardsServer.mockCardsResponseWithDelay(200, expectedCSCardsRequest, "/cscards/good_response.json")


        val requestEntity = parse(s"""
                                     |{
                                     |  "name": "John Smith",
                                     |  "creditScore": 500,
                                     |  "salary": 18000
                                     |}
         """.stripMargin).right.value

        val expectedResponse = parse(s"""
                                        |[
                                        |  {
                                        |    "provider" : "ScoredCards",
                                        |    "name" : "ScoredCard Builder",
                                        |    "apr" : 19.4,
                                        |    "cardScore" : 0.800
                                        |  }
                                        |]
         """.stripMargin).right.value


        val api = routes.orNotFound
        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"creditcards"))
          response <- api.run(request)
          _ = response.status.code should === (200)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (expectedResponse)
        } yield ()
      }
    ).unsafeRunSync()
  }

  trait Scope extends Http4sDsl[IO] with Http4sClientDsl[IO] {
    type F[A] = IO[A]

    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val clientR: Resource[F, Client[F]] = AsyncHttpClient.resource[F](clientConfig)

    def withRoutes(httpRoutes: HttpRoutes[F] => F[Unit]): F[Unit] =
      clientR.use(client => {
        val scoredCardsService = new ScoredCardsService[F](client, Uri.unsafeFromString(scoredCardsBasePath))
        val csCardsService = new CSCardsService[F](client, Uri.unsafeFromString(csCardsBasePath))

        val multiService = new MultiPartnerCreditCardRecommendationService(List(scoredCardsService, csCardsService))

        val creditCardScoringRestService = CreditCardScoringRestService(multiService)
        httpRoutes(CreditCardScoringApi(creditCardScoringRestService))
      })
  }
}
