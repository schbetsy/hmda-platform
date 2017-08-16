package hmda.api.http

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hmda.api.http.institutions.submissions._
import hmda.api.http.institutions.{ FilingPaths, InstitutionPaths, UploadPaths }
import hmda.api.protocol.processing.{ ApiErrorProtocol, InstitutionProtocol }

import scala.concurrent.ExecutionContext

trait InstitutionsHttpApi
    extends InstitutionProtocol
    with InstitutionPaths
    with FilingPaths
    with SubmissionBasePaths
    with SubmissionParseErrorsPaths
    with SubmissionEditPaths
    with SubmissionIrsPaths
    with SubmissionSignPaths
    with SubmissionSummaryPaths
    with UploadPaths
    with ApiErrorProtocol
    with HmdaCustomDirectives {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  val log: LoggingAdapter

  implicit val timeout: Timeout

  def institutionsRoutes(supervisor: ActorRef, querySupervisor: ActorRef, validationStats: ActorRef) =
    extractExecutionContext { executor =>
      implicit val ec: ExecutionContext = executor
      encodeResponse {
        headerAuthorize {
          institutionsPath(querySupervisor) ~
            pathPrefix("institutions" / Segment) { instId =>
              institutionAuthorize(instId) {
                institutionByIdPath(supervisor, querySupervisor, instId) ~
                  filingByPeriodPath(supervisor, instId) ~
                  submissionPath(supervisor, instId) ~
                  submissionLatestPath(supervisor, instId) ~
                  uploadPath(supervisor, querySupervisor, instId) ~
                  submissionEditsPath(supervisor, querySupervisor, instId) ~
                  submissionParseErrorsPath(supervisor, querySupervisor, instId) ~
                  submissionEditCsvPath(supervisor, querySupervisor, instId) ~
                  submissionSingleEditPath(supervisor, querySupervisor, instId) ~
                  editFailureDetailsPath(supervisor, querySupervisor, instId) ~
                  verifyEditsPath(supervisor, querySupervisor, instId) ~
                  submissionIrsPath(querySupervisor, validationStats, instId) ~
                  submissionIrsCsvPath(querySupervisor, validationStats, instId) ~
                  submissionSignPath(supervisor, querySupervisor, instId) ~
                  submissionSummaryPath(supervisor, querySupervisor, instId)
              }
            }
        }
      }
    }
}
