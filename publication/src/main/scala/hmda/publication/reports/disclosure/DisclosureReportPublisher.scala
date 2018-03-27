package hmda.publication.reports.disclosure

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{ Subscribe, SubscribeAck }
import akka.pattern.ask
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import akka.stream.Supervision._
import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import akka.stream.alpakka.s3.scaladsl.S3Client
import akka.stream.alpakka.s3.{ MemoryBufferType, S3Settings }
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.{ ByteString, Timeout }
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import hmda.census.model.Msa
import hmda.model.fi.{ Submission, SubmissionId }
import hmda.model.institution.Institution
import hmda.persistence.HmdaSupervisor.{ FindProcessingActor, FindSubmissions }
import hmda.persistence.messages.commands.institutions.InstitutionCommands.GetInstitutionById
import hmda.persistence.messages.events.pubsub.PubSubEvents.SubmissionSignedPubSub
import hmda.persistence.model.HmdaActor
import hmda.persistence.model.HmdaSupervisorActor.FindActorByName
import hmda.persistence.processing.SubmissionManager.GetActorRef
import hmda.persistence.processing.{ PubSubTopics, SubmissionManager }
import hmda.query.repository.filing.LoanApplicationRegisterCassandraRepository
import hmda.validation.messages.ValidationStatsMessages.FindIrsStats
import hmda.validation.stats.SubmissionLarStats
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.parser.fi.lar.LarCsvParser
import hmda.persistence.institutions.SubmissionPersistence.GetLatestAcceptedSubmission
import hmda.persistence.institutions.{ InstitutionPersistence, SubmissionPersistence }
import hmda.persistence.messages.commands.publication.PublicationCommands.GenerateDisclosureReports

import scala.concurrent.Future
import scala.concurrent.duration._

object DisclosureReportPublisher {
  val name = "disclosure-report-publisher"
  def props(): Props = Props(new DisclosureReportPublisher)
}

class DisclosureReportPublisher extends HmdaActor with LoanApplicationRegisterCassandraRepository {

  val decider: Decider = { e =>
    repositoryLog.error("Unhandled error in stream", e)
    Supervision.Resume
  }

  override implicit def system: ActorSystem = context.system
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  override implicit def materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)

  val mediator = DistributedPubSub(context.system).mediator

  mediator ! Subscribe(PubSubTopics.submissionSigned, self)

  val duration = config.getInt("hmda.actor.timeout")
  implicit val timeout = Timeout(duration.seconds)

  val accessKeyId = config.getString("hmda.publication.aws.access-key-id")
  val secretAccess = config.getString("hmda.publication.aws.secret-access-key ")
  val region = config.getString("hmda.publication.aws.region")
  val bucket = config.getString("hmda.publication.aws.public-bucket")
  val environment = config.getString("hmda.publication.aws.environment")

  val awsCredentials = new AWSStaticCredentialsProvider(
    new BasicAWSCredentials(accessKeyId, secretAccess)
  )
  val awsSettings = new S3Settings(MemoryBufferType, None, awsCredentials, region, false)
  val s3Client = new S3Client(awsSettings)

  val reports = List(
    D1, D2,
    D31, D32,
    D41, D42, D43, D44, D45, D46, D47,
    D51, D52, D53, D54, D56, D57,
    D71, D72, D73, D74, D75, D76, D77,
    D81, D82, D83, D84, D85, D86, D87,
    D11_1, D11_2, D11_3, D11_4, D11_5, D11_6, D11_7, D11_8, D11_9, D11_10,
    D12_2,
    A1, A2, A3,
    A4W,
    DiscB
  )

  val nationwideReports = List(A1W, A2W, A3W, DiscBW, DIRS)

  override def receive: Receive = {

    case SubscribeAck(Subscribe(PubSubTopics.submissionSigned, None, `self`)) =>
      log.info(s"${self.path} subscribed to ${PubSubTopics.submissionSigned}")

    case SubmissionSignedPubSub(submissionId) =>
    //self ! GenerateDisclosureReports(submissionId)

    case GenerateDisclosureReports(submissionId) =>
      log.info(s"Generating disclosure reports for ${submissionId.toString}")
      // Ignore period and sequence number in submission id. Only use institutionID
      // the generateReports method uses period 2017 and the latest signed submission for this institution.
      generateReports(submissionId.institutionId)

    case _ => //do nothing
  }

  def s3Flow(institution: Institution): Flow[DisclosureReportPayload, Future[MultipartUploadResult], NotUsed] =
    Flow[DisclosureReportPayload].map { payload =>
      val filePath = s"$environment/reports/disclosure/2017/${institution.respondent.name}/${payload.msa}/${payload.reportID}.txt"

      log.info(s"Publishing report. Institution: ${institution.id}, MSA: ${payload.msa}, Report #: ${payload.reportID}")

      Source.single(ByteString(payload.report)).runWith(s3Client.multipartUpload(bucket, filePath))
    }

  def simpleReportFlow(
    larSource: Source[LoanApplicationRegister, NotUsed],
    institution: Institution,
    msaList: List[Int]
  ): Flow[(Int, DisclosureReport), DisclosureReportPayload, NotUsed] =
    Flow[(Int, DisclosureReport)].mapAsync(1) {
      case (msa, report) => report.generate(larSource, msa, institution, msaList)
    }

  private def generateReports(institutionId: String): Future[Unit] = {
    for {
      institution <- getInstitution(institutionId)
      subId <- getLatestAcceptedSubmissionId(institutionId)
      msas <- getMSAFromIRS(subId)
    } yield {
      val msaList = msas.toList

      val sourceFileName = s"prod/lar/$institutionId.txt"
      val larSource: Source[LoanApplicationRegister, NotUsed] = s3Client
        .download(bucket, sourceFileName)
        .via(framing)
        .via(byteStringToLarFlow)

      val combinations = combine(msaList, reports) ++ combine(List(-1), nationwideReports)

      val reportFlow = simpleReportFlow(larSource, institution, msaList)
      val publishFlow = s3Flow(institution)

      Source(combinations).via(reportFlow).via(publishFlow).runWith(Sink.ignore)
    }
  }

  def framing: Flow[ByteString, ByteString, NotUsed] = {
    Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true)
  }

  val byteStringToLarFlow: Flow[ByteString, LoanApplicationRegister, NotUsed] =
    Flow[ByteString]
      .map(s => LarCsvParser(s.utf8String) match {
        case Right(lar) =>
          println(s"LAR: \t\t${lar.loan.id}")
          lar
      })

  /**
   * Returns all combinations of MSA and Disclosure Reports
   * Input:   List(407, 508) and List(D41, D42)
   * Returns: List((407, D41), (407, D42), (508, D41), (508, D42))
   */
  private def combine(a: List[Int], b: List[DisclosureReport]): List[(Int, DisclosureReport)] = {
    a.flatMap(msa => {
      List.fill(b.length)(msa).zip(b)
    })
  }

  val supervisor = system.actorSelection("/user/supervisor/singleton")

  private def getInstitution(institutionId: String): Future[Institution] = {
    val fInstitutionsActor = (supervisor ? FindActorByName(InstitutionPersistence.name)).mapTo[ActorRef]
    for {
      instPersistence <- fInstitutionsActor
      i <- (instPersistence ? GetInstitutionById(institutionId)).mapTo[Option[Institution]]
    } yield {
      val inst = i.getOrElse(Institution.empty)
      println(inst)
      inst
    }
  }

  private def getLatestAcceptedSubmissionId(institutionId: String): Future[SubmissionId] = {
    val submissionPersistence = (supervisor ? FindSubmissions(SubmissionPersistence.name, institutionId, "2017")).mapTo[ActorRef]
    for {
      subPersistence <- submissionPersistence
      latestAccepted <- (subPersistence ? GetLatestAcceptedSubmission).mapTo[Option[Submission]]
    } yield {
      val subId = latestAccepted.get.id
      println(subId)
      subId
    }
  }

  private def getMSAFromIRS(submissionId: SubmissionId): Future[Seq[Int]] = {
    for {
      manager <- (supervisor ? FindProcessingActor(SubmissionManager.name, submissionId)).mapTo[ActorRef]
      larStats <- (manager ? GetActorRef(SubmissionLarStats.name)).mapTo[ActorRef]
      stats <- (larStats ? FindIrsStats(submissionId)).mapTo[Seq[Msa]]
    } yield {
      val msas = stats.filter(m => m.id != "NA").map(m => m.id.toInt)
      println(msas)
      msas
    }
  }

}
