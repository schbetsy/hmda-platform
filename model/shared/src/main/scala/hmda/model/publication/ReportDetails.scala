package hmda.model.publication

import hmda.model.fi.SubmissionId

case class ReportDetails(
  institution: String,
  submissionId: SubmissionId,
  msaCount: Int,
  msaList: Seq[Int]
)
