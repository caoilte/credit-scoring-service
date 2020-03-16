package infrastructure

import cats.Parallel
import cats.effect.{Concurrent, Timer}
import domain.{CreditCardApplicant, CreditCardRecommendation, CreditCardRecommendationService}
import cats.implicits._
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

class MultiPartnerCreditCardRecommendationService[F[_] : Concurrent : Parallel : Timer](partners: List[CreditCardRecommendationService[F]]) extends CreditCardRecommendationService[F] {


  def timeoutTo[A](fa: F[A], after: FiniteDuration, fallback: F[A]): F[A] = {
    Concurrent[F].race(fa, Timer[F].sleep(after)).flatMap {
      case Left(a) => a.pure[F]
      case Right(_) => fallback
    }
  }

  private def singlePartnerRecommendationOrEmpty(partner: CreditCardRecommendationService[F], applicant: CreditCardApplicant):F[List[CreditCardRecommendation]] = {
    timeoutTo(partner.makeRecommendations(applicant), 3.seconds, List[CreditCardRecommendation]().pure[F]).recover {
      case _ => List()
    }
  }

  override def makeRecommendations(applicant: CreditCardApplicant): F[List[CreditCardRecommendation]] = {
    partners.parFlatTraverse[F, CreditCardRecommendation](partner => singlePartnerRecommendationOrEmpty(partner, applicant))
  }
}
