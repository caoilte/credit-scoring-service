package infrastructure.api

import cats.ApplicativeError
import cats.data.{NonEmptyChain, Validated}
import cats.implicits._
import domain.{CreditCardApplicant, CreditCardRecommendation, CreditCardRecommendationService, CreditScore, Name, Salary}
import infrastructure.api.CreditCardScoringRestService.CreditCardScoringRestResponse.{CreditCardRecommendationResponse, CreditCardRecommendationsResponse, CreditCardScoringRequestValidationFailure}
import infrastructure.api.CreditCardScoringRestService.{CreditCardScoringRestRequest, CreditCardScoringRestResponse}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

trait CreditCardScoringRestService[F[_]] {
  def requestRecommendations(httpPaintMixRequest: CreditCardScoringRestRequest): F[CreditCardScoringRestResponse]
}

object CreditCardScoringRestService {

  case class CreditCardScoringRestRequest(name: String, creditScore: Int, salary: Int)

  object CreditCardScoringRestRequest {
    implicit val decoder: Decoder[CreditCardScoringRestRequest] = deriveDecoder
  }

  type ValidatedRequest[T] = Validated[NonEmptyChain[GeneralError], T]


  def validateName(name: String): ValidatedRequest[Name] =
    if (name != null && name.length > 0) Name(name).validNec
    else GeneralError(s"Name must be non empty").invalidNec

  def validateCreditScore(creditScore: Int): ValidatedRequest[CreditScore] =
    if (creditScore >= 0 && creditScore <= 700) CreditScore(creditScore).validNec
    else GeneralError(s"Credit score must be 0-700 but was $creditScore").invalidNec

  def validateSalary(salary: Int): ValidatedRequest[Salary] =
    if (salary >= 0) Salary(salary).validNec
    else GeneralError(s"Salary must be 0-700 but was $salary").invalidNec


  def validateCreditCardScoringRestRequest(creditCardScoringRestRequest: CreditCardScoringRestRequest): ValidatedRequest[CreditCardApplicant] = {
    import creditCardScoringRestRequest._
    (
      validateName(name),
      validateCreditScore(creditScore),
      validateSalary(salary)
      ).mapN {
      case (name, creditScore, salary) =>
        CreditCardApplicant(name, creditScore, salary)
    }
  }


  def toCreditCardRecommendationResponse(creditCardRecommendation: CreditCardRecommendation): CreditCardRecommendationResponse = {
    import creditCardRecommendation._
    CreditCardRecommendationResponse(
      provider = provider.value,
      name = name.value,
      apr = apr.value,
      cardScore = eligibility.value
    )
  }

  def compareBySortingScore(a: CreditCardRecommendation, b: CreditCardRecommendation): Boolean = {
    a.sortingScore < b.sortingScore
  }

  def apply[F[_]](creditCardRecommendationService: CreditCardRecommendationService[F])(implicit err: ApplicativeError[F, Throwable]): CreditCardScoringRestService[F] =
    (creditCardScoringRestRequest: CreditCardScoringRestRequest) => {
      validateCreditCardScoringRestRequest(creditCardScoringRestRequest).fold(
        e => CreditCardScoringRequestValidationFailure(e).pure[F].widen,
        r => {
          creditCardRecommendationService
            .makeRecommendations(r)
            .map(
              recommendations => {
                CreditCardRecommendationsResponse(
                  recommendations.sortWith(compareBySortingScore).map(toCreditCardRecommendationResponse)
                )
              }
            )
        }
      )

    }


  sealed trait CreditCardScoringRestResponse

  object CreditCardScoringRestResponse {
    case class CreditCardScoringRequestValidationFailure(errors: NonEmptyChain[GeneralError]) extends CreditCardScoringRestResponse

    object CreditCardScoringRequestValidationFailure {
      implicit val generalErrorEncoder: Encoder[GeneralError] = deriveEncoder
      implicit val encoder: Encoder[CreditCardScoringRequestValidationFailure] = deriveEncoder
    }

    case class CreditCardRecommendationResponse(provider: String, name: String, apr: BigDecimal, cardScore: BigDecimal)
    case class CreditCardRecommendationsResponse(recommendations: List[CreditCardRecommendationResponse]) extends CreditCardScoringRestResponse

    object CreditCardRecommendationResponse {
      implicit val encoder: Encoder[CreditCardRecommendationResponse] = deriveEncoder
    }
  }

  case class GeneralError(errorMessage: String)
}
