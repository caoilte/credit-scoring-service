package domain

case class Name(value: String)
case class CreditScore(value: Int)
case class Salary(value: Int)

case class CreditCardApplicant(name: Name, creditScore: CreditScore, salary: Salary)

case class CardProvider(value: String)
case class CardName(value: String)
case class EligibilityRating(value: BigDecimal)
case class APR(value: BigDecimal)

case class CreditCardRecommendation(provider: CardProvider, name: CardName, eligibility: EligibilityRating, apr: APR) {
  def sortingScore:BigDecimal = eligibility.value * ((BigDecimal(1) / apr.value).pow(2))
}

trait CreditCardRecommendationService[F[_]] {
  def makeRecommendations(applicant: CreditCardApplicant):F[List[CreditCardRecommendation]]
}
