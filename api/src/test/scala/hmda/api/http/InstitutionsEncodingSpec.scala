package hmda.api.http

import akka.http.scaladsl.model.headers.{ HttpEncoding, `Accept-Encoding` }
import akka.http.scaladsl.model.headers.HttpEncodings._
import hmda.validation.messages.ValidationStatsMessages.AddSubmissionLarStatsActorRef
import hmda.validation.stats.SubmissionLarStats.createSubmissionStats
import org.scalacheck.Gen
import hmda.model.fi.SubmissionId

class InstitutionsEncodingSpec extends InstitutionHttpApiSpec {

  "Endpoint Response Encodings" must {
    def encodingChooser: HttpEncoding = {
      Gen.oneOf(deflate, gzip, identity).sample.getOrElse(deflate)
    }

    "use requested encoding for institutions path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions").addHeader(`Accept-Encoding`(encoding)) ~>
        institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for institutionById path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0").addHeader(`Accept-Encoding`(encoding)) ~>
        institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for filingByPeriod path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0/filings/2017")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for submission path" in {
      val encoding = encodingChooser
      postWithCfpbHeaders("/institutions/0/filings/2017/submissions")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for submissionLatest path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0/filings/2017/submissions/latest")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for upload path" in {
      val encoding = encodingChooser
      val badFile = multiPartFile("bad content", "sample.dat")
      postWithCfpbHeaders("/institutions/0/filings/2017/submissions/1", badFile)
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for submissionEdits path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0/filings/2017/submissions/1/edits")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for submissionIrs path" in {
      val subId = SubmissionId("0", "2017", 1)
      val larStats = createSubmissionStats(system, subId)
      validationStats ! AddSubmissionLarStatsActorRef(larStats, subId)
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0/filings/2017/submissions/1/irs")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for submissionSign path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0/filings/2017/submissions/1/sign")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }

    "use requested encoding for submissionSummary path" in {
      val encoding = encodingChooser
      getWithCfpbHeaders("/institutions/0/filings/2017/submissions/1/summary")
        .addHeader(`Accept-Encoding`(encoding)) ~> institutionsRoutes(supervisor, querySupervisor, validationStats) ~> check {
          response.encoding mustBe encoding
        }
    }
  }
}
