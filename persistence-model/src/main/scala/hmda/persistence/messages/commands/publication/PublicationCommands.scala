package hmda.persistence.messages.commands.publication

import hmda.model.fi.SubmissionId
import hmda.persistence.messages.CommonMessages.Command

object PublicationCommands {
  case class GenerateDisclosureReports(submissionId: SubmissionId) extends Command
  case class GenerateDisclosureNationwide(institutionId: String)
  case class GenerateDisclosureForMSA(institutionId: String, msa: Int)
  case class GetReportDetails(institutionId: String)
  case class GenerateAggregateReports() extends Command
}
