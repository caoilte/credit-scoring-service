package infrastructure.cscards


import cats.effect.Sync
import domain.{APR, CardName, CardProvider, CreditCardApplicant, CreditCardRecommendation, CreditCardRecommendationService, EligibilityRating}
import io.circe.{Decoder, Encoder}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import cats.implicits._
import io.circe.generic.semiauto._
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}

import scala.math.BigDecimal.RoundingMode

case class CSCardsRequest(name: String, creditScore: Int)

object CSCardsRequest {
  implicit val encoder: Encoder[CSCardsRequest] = deriveEncoder
}

case class CSCardsRecommendation(cardName: String, apr: BigDecimal, eligibility: BigDecimal)

object CSCardsRecommendation {
  implicit val encoder: Decoder[CSCardsRecommendation] = deriveDecoder
}

class CSCardsService[F[_] : Sync](client: Client[F], basePath: Uri)
    extends CreditCardRecommendationService[F]
    with Http4sDsl[F]
    with Http4sClientDsl[F]
    with CirceEntityDecoder
    with CirceEntityEncoder {

  override def makeRecommendations(applicant: CreditCardApplicant): F[List[CreditCardRecommendation]] = {
    val request = CSCardsService.assembleCSCardsRequest(applicant)
    val req = POST(request, (basePath / "v1" / "cards"))
    for {
      response <- client.expect[List[CSCardsRecommendation]](req)
    } yield response.map(CSCardsService.assembleCreditCardRecommendation)
  }
}

object CSCardsService {
  val PROVIDER = CardProvider("CSCards")

  def assembleCSCardsRequest(applicant: CreditCardApplicant): CSCardsRequest = {
    import applicant._

    CSCardsRequest(
      name = name.value,
      creditScore = creditScore.value
    )
  }

  def assembleCreditCardRecommendation(cscRecommendation: CSCardsRecommendation): CreditCardRecommendation = {
    import cscRecommendation._
    CreditCardRecommendation(
      provider = PROVIDER,
      name = CardName(cardName),
      eligibility = EligibilityRating(eligibility / BigDecimal(10).setScale(3, RoundingMode.HALF_UP)),
      apr = APR(apr)
    )
  }
}
