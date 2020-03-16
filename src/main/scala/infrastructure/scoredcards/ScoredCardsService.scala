package infrastructure.scoredcards

import cats.effect.Sync
import cats.implicits._
import domain._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.http4s.Uri
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl

import scala.math.BigDecimal.RoundingMode

case class ScoredCardsRequest(name: String, score: Int, salary: Int)

object ScoredCardsRequest {
  implicit val encoder: Encoder[ScoredCardsRequest] = deriveEncoder
}

case class ScoredCardsRecommendation(card: String, apr: BigDecimal, approvalRating: BigDecimal)

object ScoredCardsRecommendation {
  implicit val encoder: Decoder[ScoredCardsRecommendation] = deriveDecoder
}

class ScoredCardsService[F[_] : Sync](client: Client[F], basePath: Uri)
    extends CreditCardRecommendationService[F]
    with Http4sDsl[F]
    with Http4sClientDsl[F]
    with CirceEntityDecoder
    with CirceEntityEncoder {

  override def makeRecommendations(applicant: CreditCardApplicant): F[List[CreditCardRecommendation]] = {
    val request = ScoredCardsService.assembleScoredCardsRequest(applicant)
    val req = POST(request, (basePath / "v2" / "creditcards"))
    for {
      response <- client.expect[List[ScoredCardsRecommendation]](req)
    } yield response.map(ScoredCardsService.assembleCreditCardRecommendation)
  }
}

object ScoredCardsService {
  val PROVIDER = CardProvider("ScoredCards")

  def assembleScoredCardsRequest(applicant: CreditCardApplicant): ScoredCardsRequest = {
    import applicant._
    ScoredCardsRequest(
      name = name.value,
      score = creditScore.value,
      salary = salary.value
    )
  }

  def assembleCreditCardRecommendation(scoredCardsRecommendation: ScoredCardsRecommendation): CreditCardRecommendation = {
    import scoredCardsRecommendation._
    CreditCardRecommendation(
      provider = PROVIDER,
      name = CardName(card),
      eligibility = EligibilityRating(approvalRating.setScale(3, RoundingMode.HALF_UP)),
      apr = APR(apr)
    )
  }
}
