package hmda.api.http.admin

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hmda.api.http.HmdaCustomDirectives
import hmda.api.model.ErrorResponse
import hmda.api.protocol.processing.ApiErrorProtocol
import hmda.model.fi.{ Signed, Submission, SubmissionId }
import hmda.persistence.HmdaSupervisor.FindSubmissions
import hmda.persistence.institutions.SubmissionPersistence
import hmda.persistence.institutions.SubmissionPersistence.GetSubmissionById
import hmda.persistence.messages.commands.publication.PublicationCommands.{ GenerateAggregateReports, GenerateDisclosureReports }
import hmda.persistence.messages.events.pubsub.PubSubEvents.{ FindAggregatePublisher, FindDisclosurePublisher }

import scala.util.{ Failure, Success }

trait PublicationAdminHttpApi extends HmdaCustomDirectives with ApiErrorProtocol {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val timeout: Timeout

  val log: LoggingAdapter

  def disclosureGenerationPath(supervisor: ActorRef, publicationSupervisor: ActorRef) =
    path("disclosure" / Segment / IntNumber / IntNumber) { (instId, year, subId) =>
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedPost { uri =>
          val submissionId = SubmissionId(instId, year.toString, subId)

          val message = for {
            p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
          } yield {
            p ! GenerateDisclosureReports(submissionId)
          }

          onComplete(message) {
            case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
            case Failure(error) =>
              completeWithInternalError(uri, error)
          }
        }
      }
    }

  def aggregateGenerationPath(supervisor: ActorRef, publicationSupervisor: ActorRef) =
    path("aggregate" / "2017") {
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedPost { uri =>
          val publisherF = (publicationSupervisor ? FindAggregatePublisher()).mapTo[ActorRef]
          val msg = publisherF.map(_ ! GenerateAggregateReports())

          onComplete(msg) {
            case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
            case Failure(error) => completeWithInternalError(uri, error)
          }
        }
      }
    }

  def publicationRoutes(supervisor: ActorRef, publicationSupervisor: ActorRef) =
    disclosureGenerationPath(supervisor, publicationSupervisor) ~ aggregateGenerationPath(supervisor, publicationSupervisor)

}
