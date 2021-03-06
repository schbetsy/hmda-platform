package hmda.persistence.serialization.validation

import hmda.persistence.messages.events.validation.SubmissionLarStatsEvents.{ IrsStatsUpdated, MacroStatsUpdated, SubmittedLarsUpdated, ValidatedLarsUpdated }
import hmda.persistence.model.MsaGenerators
import org.scalacheck.Gen
import org.scalatest.{ MustMatchers, PropSpec }
import org.scalatest.prop.PropertyChecks

class SubmissionLarStatsProtobufSerializerSpec extends PropSpec with PropertyChecks with MustMatchers with MsaGenerators {
  val serializer = new SubmissionLarStatsProtobufSerializer()

  property("SubmittedLarsUpdated messages must be serialized to binary and back") {
    forAll(Gen.choose(0, 100000)) { total =>
      val msg = SubmittedLarsUpdated(totalSubmitted = total)
      val bytes = serializer.toBinary(msg)
      serializer.fromBinary(bytes, serializer.SubmittedLarsUpdatedManifest) mustBe msg
    }
  }

  property("ValidatedLarsUpdated message must be serialized to binary and back") {
    forAll(Gen.choose(0, 100000)) { total =>
      val msg = ValidatedLarsUpdated(total)
      val bytes = serializer.toBinary(msg)
      serializer.fromBinary(bytes, serializer.ValidatedLarsUpdatedManifest) mustBe msg
    }
  }

  property("MacroStatsUpdated messages must be serialized to binary and back") {
    val intGen = Gen.choose(0, 1000)
    forAll(Gen.choose(0d, 1d)) { d =>
      val msg = MacroStatsUpdated(
        totalValidated = intGen.sample.get,
        q070Total = intGen.sample.get,
        q070Sold = intGen.sample.get,
        q071Total = intGen.sample.get,
        q071Sold = intGen.sample.get,
        q072Total = intGen.sample.get,
        q072Sold = intGen.sample.get,
        q075Ratio = d,
        q076Ratio = d
      )
      val bytes = serializer.toBinary(msg)
      serializer.fromBinary(bytes, serializer.MacroStatsUpdatedManifest) mustBe msg
    }
  }

  property("IrsStatsUpdated messages must serialized to binary and back") {
    forAll(listOfMsaGen) { list =>
      val msg = IrsStatsUpdated(list)
      val bytes = serializer.toBinary(msg)
      serializer.fromBinary(bytes, serializer.IrsStatsUpdatedManifest) mustBe msg
    }
  }
}
