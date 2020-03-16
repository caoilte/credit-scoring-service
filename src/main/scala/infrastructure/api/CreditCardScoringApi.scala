package infrastructure.api

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import infrastructure.api.CreditCardScoringRestService.CreditCardScoringRestRequest
import infrastructure.api.CreditCardScoringRestService.CreditCardScoringRestResponse.{
  CreditCardRecommendationsResponse, CreditCardScoringRequestValidationFailure
}

object CreditCardScoringApi extends CirceEntityDecoder with CirceEntityEncoder {

  def apply[F[_] : Sync](creditCardScoringRestService: CreditCardScoringRestService[F]): HttpRoutes[F] = {
    val http4sDsl = Http4sDsl[F]
    import http4sDsl._

    HttpRoutes.of[F] {
      case req @ POST -> Root / "creditcards" =>
        req.decodeStrict[CreditCardScoringRestRequest] { request =>
          (creditCardScoringRestService
            .requestRecommendations(request)
            .flatMap {
              case r: CreditCardScoringRequestValidationFailure => BadRequest(r)
              case r: CreditCardRecommendationsResponse         => Ok(r.recommendations)
            })
            .recoverWith {
              case e: Exception => {
                println(e)
                InternalServerError("Something went wrong")
              }
            }
        }
    }
  }

}
