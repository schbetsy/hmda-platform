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
import hmda.api.protocol.processing.ApiErrorProtocol
import hmda.api.protocol.publication.ReportDetailsProtocol
import hmda.model.publication.ReportDetails
import hmda.persistence.messages.commands.publication.PublicationCommands._
import hmda.persistence.messages.events.pubsub.PubSubEvents.{ FindAggregatePublisher, FindDisclosurePublisher }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait PublicationAdminHttpApi extends HmdaCustomDirectives with ApiErrorProtocol with ReportDetailsProtocol {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val timeout: Timeout

  val log: LoggingAdapter

  def disclosureNationwidePath(publicationSupervisor: ActorRef) =
    path("disclosure" / "institution" / Segment / "nationwide") { (instId) =>
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedPost { uri =>

          val message = for {
            p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
          } yield {
            p ! GenerateDisclosureNationwide(instId)
          }

          onComplete(message) {
            case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
            case Failure(error) =>
              completeWithInternalError(uri, error)
          }
        }
      }
    }

  def disclosureMSAPath(publicationSupervisor: ActorRef) =
    path("disclosure" / "institution" / Segment / IntNumber) { (instId, msa) =>
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedPost { uri =>

          val message = for {
            p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
          } yield {
            p ! GenerateDisclosureForMSA(instId, msa)
          }

          onComplete(message) {
            case Success(sub) => complete(ToResponseMarshallable(StatusCodes.OK))
            case Failure(error) =>
              completeWithInternalError(uri, error)
          }
        }
      }
    }

  def disclosurePrepPath(publicationSupervisor: ActorRef) =
    path("disclosure" / "info" / Segment) { (instId) =>
      extractExecutionContext { executor =>
        implicit val ec = executor
        timedGet { uri =>

          val details: Future[ReportDetails] = for {
            p <- (publicationSupervisor ? FindDisclosurePublisher()).mapTo[ActorRef]
            d <- (p ? GetReportDetails(instId)).mapTo[ReportDetails]
          } yield d

          onComplete(details) {
            case Success(sub) => complete(ToResponseMarshallable(details))
            case Failure(error) =>
              completeWithInternalError(uri, error)
          }
        }
      }
    }

  def publicationRoutes(supervisor: ActorRef, publicationSupervisor: ActorRef) =
    disclosurePrepPath(publicationSupervisor) ~
      disclosureNationwidePath(publicationSupervisor) ~
      disclosureMSAPath(publicationSupervisor)

  /*
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
  */

}
