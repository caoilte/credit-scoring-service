package server

import cats.effect.{ExitCode, IO, IOApp}
import infrastructure.MultiPartnerCreditCardRecommendationService
import infrastructure.api.{CreditCardScoringApi, CreditCardScoringRestService}
import infrastructure.cscards.CSCardsService
import infrastructure.scoredcards.ScoredCardsService
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s.Uri
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import fs2._
import cats.implicits._

object Main extends IOApp {

  // $COVERAGE-OFF$
  override def run(args: List[String]): IO[ExitCode] = {
    val port = args(0).toInt
    val cscardsEndpoint = args(1)
    val scoredCardsEndpoint = args(2)

    val clientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    val appStream: Stream[IO, ExitCode] = for {
      client <- AsyncHttpClient.stream[IO](clientConfig)
      scoredCardsService = new ScoredCardsService[IO](client, Uri.unsafeFromString(scoredCardsEndpoint))
      csCardsService = new CSCardsService[IO](client, Uri.unsafeFromString(cscardsEndpoint))
      multiService = new MultiPartnerCreditCardRecommendationService(List(scoredCardsService, csCardsService))
      creditCardScoringRestService = CreditCardScoringRestService(multiService)
      serverBuild = BlazeServerBuilder[IO].withNio2(true).bindHttp(port, "0.0.0.0")
      httpApp = CreditCardScoringApi(creditCardScoringRestService).orNotFound
      _ = println("App Started")
      exitCode <- serverBuild.withHttpApp(httpApp).serve
      _ = println("App Shutting Down")
    } yield exitCode
    appStream.compile.lastOrError.as(ExitCode.Success)
  }
  // $COVERAGE-ON$
}
