package hmda.api.protocol.publication

import hmda.api.protocol.processing.SubmissionProtocol
import hmda.model.publication.ReportDetails

trait ReportDetailsProtocol extends SubmissionProtocol {

  implicit val reportDetailsProtocol = jsonFormat4(ReportDetails.apply)

}
