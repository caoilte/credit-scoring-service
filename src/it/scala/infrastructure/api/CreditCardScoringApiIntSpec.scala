package infrastructure.api

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.http4s.implicits._
import domain.{APR, CardName, CardProvider, CreditCardApplicant, CreditCardRecommendation, CreditCardRecommendationService, EligibilityRating}
import io.circe.Json
import io.circe.parser.parse
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.scalatest.{EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class CreditCardScoringApiIntSpec extends AnyFlatSpec  with EitherValues with Matchers {

  // make trait
  implicit lazy val ec: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit protected def contextShift: ContextShift[IO] = IO.contextShift(ec)

  // make trait

  behavior of "Credit Card Scoring API"

  it should "return a valid response as a 200" in new Scope {

    val fakeCreditCardRecommendationService = new CreditCardRecommendationService[IO] {
      override def makeRecommendations(applicant: CreditCardApplicant): IO[List[CreditCardRecommendation]] = {
        List(
          CreditCardRecommendation(
            provider = CardProvider("ScoredCards"),
            name = CardName("ScoredCard Builder"),
            eligibility = EligibilityRating(0.212),
            apr = APR(19.4)
          )
        ).pure[IO]
      }
    }

    val expectedResponse = parse(s"""
                                   |[
                                   |  {
                                   |    "provider" : "ScoredCards",
                                   |    "name" : "ScoredCard Builder",
                                   |    "apr" : 19.4,
                                   |    "cardScore" : 0.212
                                   |  }
                                   |]
         """.stripMargin).right.value

    withAPI(
      fakeCreditCardRecommendationService,
      routes => {
        val api = routes.orNotFound

        val requestEntity = parse(s"""
                                     |{
                                     |  "name" : "John Smith",
                                     |  "creditScore" : 500,
                                     |  "salary" : 28000
                                     |}
         """.stripMargin).right.value
        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"creditcards"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = jsonResponse should === (expectedResponse)
          _ = response.status.code should === (200)
        } yield ()
      }
    ).unsafeRunSync()
  }

  it should "reject invalid input with useful error messages and return 400" in new Scope {

    val fakeCreditCardRecommendationService = new CreditCardRecommendationService[IO] {
      override def makeRecommendations(applicant: CreditCardApplicant): IO[List[CreditCardRecommendation]] = {
        new Throwable("Should not be called").raiseError[IO, List[CreditCardRecommendation]]
      }
    }

    val expectedResponse = parse(s"""
                                    |{
                                    |  "errors" : [
                                    |    {
                                    |      "errorMessage" : "Name must be non empty"
                                    |    },
                                    |    {
                                    |      "errorMessage" : "Credit score must be 0-700 but was 1200"
                                    |    },
                                    |    {
                                    |      "errorMessage" : "Salary must be 0-700 but was -10"
                                    |    }
                                    |  ]
                                    |}
         """.stripMargin).right.value

    withAPI(
      fakeCreditCardRecommendationService,
      routes => {
        val api = routes.orNotFound

        val requestEntity = parse(s"""
                                     |{
                                     |  "name" : "",
                                     |  "creditScore" : 1200,
                                     |  "salary" : -10
                                     |}
         """.stripMargin).right.value
        for {
          request <- POST(requestEntity, Uri.unsafeFromString(s"creditcards"))
          response <- api.run(request)
          jsonResponse <- response.as[Json]
          _ = response.status.code should === (400)
          _ = jsonResponse should === (expectedResponse)
        } yield ()
      }
    ).unsafeRunSync()
  }


    trait Scope extends Http4sDsl[IO] with Http4sClientDsl[IO] {
    type F[A] = IO[A]

    def withAPI(fakeCreditCardRecommendationService: CreditCardRecommendationService[F], httpRoutes: HttpRoutes[F] => F[Unit]): F[Unit] = {
      val creditCardScoringRestService = CreditCardScoringRestService(fakeCreditCardRecommendationService)
      httpRoutes(CreditCardScoringApi(creditCardScoringRestService))
    }

  }

}
