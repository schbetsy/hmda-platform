package hmda.publication.reports.disclosure

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.publication.reports._
import hmda.query.model.filing.LoanApplicationRegisterQuery

import scala.concurrent.Future

object D51 {

  def filters(lar: LoanApplicationRegisterQuery): Boolean = {
    (lar.loanType == 2 || lar.loanType == 3 || lar.loanType == 4) &&
      (lar.propertyType == 1 || lar.propertyType == 2) &&
      (lar.purpose == 1)
  }

  def generate[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegisterQuery, NotUsed],
    fipsCode: Int,
    respondentId: String,
    institutionNameF: Future[String]
  ): Future[D5X] = {

    D5X.generate("D51", filters, larSource, fipsCode, respondentId, institutionNameF)
  }
}
