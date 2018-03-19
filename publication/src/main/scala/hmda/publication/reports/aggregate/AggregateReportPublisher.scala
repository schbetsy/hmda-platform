package hmda.publication.reports.aggregate

import java.util.concurrent.CompletionStage

import akka.NotUsed
import akka.actor.{ ActorSystem, Props }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Supervision }
import akka.stream.Supervision._
import akka.stream.alpakka.s3.javadsl.S3Client
import akka.stream.alpakka.s3.{ MemoryBufferType, S3Settings }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.{ ByteString, Timeout }
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import hmda.persistence.model.HmdaActor
import hmda.query.repository.filing.LoanApplicationRegisterCassandraRepository
import akka.stream.alpakka.s3.javadsl.MultipartUploadResult
import hmda.census.model.MsaIncomeLookup
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports.ApplicantIncomeEnum._
import hmda.model.publication.reports._
import hmda.persistence.messages.commands.publication.PublicationCommands.GenerateAggregateReports
import hmda.publication.reports.util.DispositionType._
import hmda.publication.reports.util.ReportUtil._
import hmda.publication.reports.util._
import hmda.util.SourceUtils

import scala.concurrent.duration._
import scala.util.Try

object AggregateReportPublisher {
  val name = "aggregate-report-publisher"
  def props(): Props = Props(new AggregateReportPublisher)
}

class AggregateReportPublisher extends HmdaActor with LoanApplicationRegisterCassandraRepository
    with SourceUtils {

  val decider: Decider = { e =>
    repositoryLog.error("Unhandled error in stream", e)
    Supervision.Resume
  }

  override implicit def system: ActorSystem = context.system
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  override implicit def materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)

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
  val s3Client = new S3Client(awsSettings, context.system, materializer)

  val aggregateReports: List[AggregateReport] = List(
    A2,
    AggregateA1, AggregateA2, AggregateA3,
    AggregateA4,
    AggregateB,
    A32,
    A42, A43, A45, A46, A47,
    A51, A52, A53, A54, A56, A57,
    A71, A72, A73, A74, A75, A76, A77,
    A81, A82, A83, A84, A85, A86, A87,
    A9,
    A11_1, A11_2, A11_3, A11_4, A11_5, A11_6, A11_7, A11_8, A11_9, A11_10,
    A12_1, A12_2
  )

  val nationalAggregateReports: List[AggregateReport] = List(
    NationalAggregateA1, NationalAggregateA2, NationalAggregateA3,
    NationalAggregateA4,
    NationalAggregateB,
    N32,
    N41, N43, N45, N46, N47,
    N51, N52, N53, N54, N56, N57,
    N71, N72, N73, N74, N75, N76, N77,
    N81, N82, N83, N84, N85, N86, N87,
    N9,
    N11_1, N11_2, N11_3, N11_4, N11_5, N11_6, N11_7, N11_8, N11_9, N11_10,
    N12_1, N12_2
  )

  override def receive: Receive = {

    case GenerateAggregateReports() =>
      log.info(s"Generating aggregate reports for 2017 filing year")
      //generateReports
      checkFilters

    case _ => //do nothing
  }

  private def checkFilters = {
    val larSource = readData(1000)

    val dispositions = List(
      FannieMae, FreddieMac, GinnieMae, FarmerMac, PrivateSecuritization, CommercialBank, FinanceCompany, Affiliate, OtherPurchaser
      //DebtToIncomeRatio, EmploymentHistory, CreditHistory, Collateral, InsufficientCash, UnverifiableInformation, CreditAppIncomplete, MortgageInsuranceDenied, OtherDenialReason, TotalDenied,
      //FHA, Conventional, Refinancings, HomeImprovementLoans, LoansForFiveOrMore, NonoccupantLoans, ManufacturedHomeDwellings,
      //PreapprovalsToOriginations, PreapprovalsNotAccepted, PreApprovalsDenied,
      //ApplicationReceived, LoansOriginated, ApprovedButNotAccepted, ApplicationsDenied, ApplicationsWithdrawn, ClosedForIncompleteness, LoanPurchased, PreapprovalDenied, PreapprovalApprovedButNotAccepted
    )

    calculateYear(larSource).map(result => s"calculateYear: ${println(result)}")
    println(s"formattedCurrentDate: $formattedCurrentDate")
    println(s" ====> There should be ${dispositions.size} total dispositions.")

    dispositions.map { disp =>
      disp.calculateValueDisposition(larSource).map(println)
    }

    /* metadata looks fine
    aggregateReports.map { report =>
      val meta = ReportsMetaDataLookup.values(report.reportId)
      println(s"${meta.reportType} - ${meta.reportTable} - ${meta.description}")
    }

    nationalAggregateReports.map { report =>
      val meta = ReportsMetaDataLookup.values(report.reportId)
      println(s"${meta.reportType} - ${meta.reportTable} - ${meta.description}")
    }
    */

    /* TODO: Try income and identity filters
    //val larsReportingIncome = larSource.filter(lar => Try(lar.applicant.income.toDouble).isSuccess)
    val larsReportingIncome = larSource.filter { lar =>
      lar.applicant.income != "NA" && lar.geography.msa != "NA"
    }

    count(larsReportingIncome).map { total =>
      println(s"LARs with income != NA: $total")
    }

    def income(lar: LoanApplicationRegister): Int = lar.applicant.income.toInt
    sum(larsReportingIncome, income)
      .map { total =>
        println(s"LARs TOTAL of all reported incomes: $total")
      }

    val incomeIntervalsNational: Map[ApplicantIncomeEnum, Source[LoanApplicationRegister, NotUsed]] = nationalLarsByIncomeInterval(larsReportingIncome)
    count(incomeIntervalsNational(LessThan50PercentOfMSAMedian)).map { total =>
      println(s"# of Lars NATIONALLY with LessThan50PercentOfMSAMedian: $total")
    }
    count(incomeIntervalsNational(Between50And79PercentOfMSAMedian)).map { total =>
      println(s"# of Lars NATIONALLY with Between50and79Percent: $total")
    }
    count(incomeIntervalsNational(Between80And99PercentOfMSAMedian)).map { total =>
      println(s"# of Lars NATIONALLY with Between80and99Percent: $total")
    }
    count(incomeIntervalsNational(Between100And119PercentOfMSAMedian)).map { total =>
      println(s"# of Lars NATIONALLY with Between100And119Percent: $total")
    }
    count(incomeIntervalsNational(GreaterThan120PercentOfMSAMedian)).map { total =>
      println(s"# of Lars NATIONALLY with GreaterThan120Percent: $total")
    }

    val incomeIntervalsMSA = larsByIncomeInterval(larsReportingIncome, calculateMedianIncomeIntervals(12060))
    count(incomeIntervalsMSA(LessThan50PercentOfMSAMedian)).map { total =>
      println(s"# of Lars in MSA 12060 with LessThan50PercentOfMSAMedian: $total")
    }
    count(incomeIntervalsMSA(Between50And79PercentOfMSAMedian)).map { total =>
      println(s"# of Lars in MSA 12060 with Between50and79Percent: $total")
    }
    count(incomeIntervalsMSA(Between80And99PercentOfMSAMedian)).map { total =>
      println(s"# of Lars in MSA 12060 with Between80and99Percent: $total")
    }
    count(incomeIntervalsMSA(Between100And119PercentOfMSAMedian)).map { total =>
      println(s"# of Lars in MSA 12060 with Between100And119Percent: $total")
    }
    count(incomeIntervalsMSA(GreaterThan120PercentOfMSAMedian)).map { total =>
      println(s"# of Lars in MSA 12060 with GreaterThan120Percent: $total")
    }

    val ethnicities = EthnicityEnum.values
    ethnicities.map { eth =>
      count(EthnicityUtil.filterEthnicity(larSource, eth)).map { total =>
        println(s"ETHNICITY: # of LARs with $eth: $total")
      }
    }

    val races = RaceEnum.values
    races.map { race =>
      count(RaceUtil.filterRace(larSource, race)).map { total =>
        println(s"RACE: # of LARs with $race: $total")
      }
    }

    val minorityStatuses = MinorityStatusEnum.values
    minorityStatuses.map { ms =>
      count(MinorityStatusUtil.filterMinorityStatus(larSource, ms)).map { total =>
        println(s"MINORITY: # of LARs with $ms: $total")
      }
    }

    val genders = GenderEnum.values
    genders.map { g =>
      count(GenderUtil.filterGender(larSource, g)).map { total =>
        println(s"GENDER: # of LARs with $g: $total")
      }
    }
    */

  }

  private def generateReports = {
    val larSource = readData(1000)
    //val msaList = MsaIncomeLookup.everyFips.toList
    val msaList = List(12060, 42220)

    val combinations = combine(msaList, aggregateReports) //++ combine(List(-1), nationalAggregateReports)

    val simpleReportFlow: Flow[(Int, AggregateReport), AggregateReportPayload, NotUsed] =
      Flow[(Int, AggregateReport)].mapAsyncUnordered(1) {
        case (msa, report) => report.generate(larSource, msa)
      }

    val s3Flow: Flow[AggregateReportPayload, CompletionStage[MultipartUploadResult], NotUsed] =
      Flow[AggregateReportPayload]
        .map(payload => {
          val filePath = s"$environment/reports/aggregate/2017/${payload.msa}/${payload.reportID}.txt"
          log.info(s"Publishing Aggregate report. MSA: ${payload.msa}, Report #: ${payload.reportID}")

          Source.single(ByteString(payload.report))
            .runWith(s3Client.multipartUpload(bucket, filePath))
        })

    Source(combinations).via(simpleReportFlow).via(s3Flow).runWith(Sink.ignore)
  }

  /**
   * Returns all combinations of MSA and Aggregate Reports
   * Input:   List(407, 508) and List(A41, A42)
   * Returns: List((407, A41), (407, A42), (508, A41), (508, A42))
   */
  private def combine(msas: List[Int], reports: List[AggregateReport]): List[(Int, AggregateReport)] = {
    msas.flatMap(msa => List.fill(reports.length)(msa).zip(reports))
  }

}

