package hmda.publication.reports.aggregate

import akka.NotUsed
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.model.publication.reports._
import hmda.model.publication.reports.GenderEnum._
import hmda.publication.reports._
import hmda.publication.reports.protocol._
import hmda.publication.reports.util.DispositionType.{ ApprovedButNotAcceptedDisp, OriginatedDisp, ReceivedDisp }
import hmda.publication.reports.util.EthnicityUtil._
import hmda.publication.reports.util.GenderUtil._
import hmda.publication.reports.util.MinorityStatusUtil._
import hmda.publication.reports.util.RaceUtil._
import hmda.publication.reports.util.ReportUtil._
import spray.json._

import scala.concurrent.Future

object A42
    extends RaceEnumProtocol
    with EthnicityEnumProtocol
    with MinorityStatusEnumProtocol
    with GenderEnumProtocol
    with CharacteristicProtocol
    with DispositionProtocol {

  def filters(lar: LoanApplicationRegister): Boolean = {
    (lar.loan.loanType == 1) &&
      (lar.loan.propertyType == 1 || lar.loan.propertyType == 2) &&
      (lar.loan.purpose == 1)
  }

  val dispositions = List(ReceivedDisp, OriginatedDisp, ApprovedButNotAcceptedDisp)

  def generate[ec: EC, mat: MAT, as: AS](
    larSource: Source[LoanApplicationRegister, NotUsed],
    fipsCode: Int
  ): Future[JsObject] = {

    val source = larSource.filter(filters)

    val races = RaceEnum.values.map(race => dispositionsByGender(filterRace(source, race)))
    val ethnicities = EthnicityEnum.values.map(eth => dispositionsByGender(filterEthnicity(source, eth)))
    val minorityStatuses = MinorityStatusEnum.values.map(ms => dispositionsByGender(filterMinorityStatus(source, ms)))

    for {
      raceDispositions <- Future.sequence(races)
      ethnicityDispositions <- Future.sequence(ethnicities)
      minorityStatusDispositions <- Future.sequence(minorityStatuses)
      year <- calculateYear(larSource)
    } yield {
      JsObject(
        ("races", JsArray(raceJson(raceDispositions))),
        ("ethnicities", JsArray(ethnicityJson(ethnicityDispositions))),
        ("minorityStatuses", JsArray(minorityStatusJson(minorityStatusDispositions))),
        ("reportYear", JsString(year.toString))
      )
    }
  }

  private def raceJson(dispositions: IndexedSeq[JsObject]): Vector[JsObject] = {
    val raceResults: IndexedSeq[(RaceEnum, JsObject)] = RaceEnum.values.zip(dispositions)

    raceResults.map {
      case (race, json) => JsObject(
        ("race", JsString(race.description)),
        ("dispositions", json)
      )
    }.toVector
  }
  private def ethnicityJson(dispositions: IndexedSeq[JsObject]): Vector[JsObject] = {
    val ethResults: IndexedSeq[(EthnicityEnum, JsObject)] = EthnicityEnum.values.zip(dispositions)

    ethResults.map {
      case (race, json) => JsObject(
        ("ethnicity", JsString(race.description)),
        ("dispositions", json)
      )
    }.toVector
  }
  private def minorityStatusJson(dispositions: IndexedSeq[JsObject]): Vector[JsObject] = {
    val msResults: IndexedSeq[(MinorityStatusEnum, JsObject)] = MinorityStatusEnum.values.zip(dispositions)

    msResults.map {
      case (race, json) => JsObject(
        ("minorityStatus", JsString(race.description)),
        ("dispositions", json)
      )
    }.toVector
  }

  private def dispositionsByGender[ec: EC, mat: MAT, as: AS](larSource: Source[LoanApplicationRegister, NotUsed]): Future[JsObject] = {

    val all = calculateDispositions(larSource, dispositions)
    val male: Future[List[Disposition]] = calculateDispositions(filterGender(larSource, Male), dispositions)
    val female = calculateDispositions(filterGender(larSource, Female), dispositions)
    val joint = calculateDispositions(filterGender(larSource, Joint), dispositions)

    for {
      m: List[Disposition] <- male
      f: List[Disposition] <- female
      j: List[Disposition] <- joint
      a: List[Disposition] <- all
    } yield {
      JsObject(
        ("dispositions", a.toJson),
        ("genders", JsArray(
          GenderCharacteristic(Male, m).toJson,
          GenderCharacteristic(Female, f).toJson,
          GenderCharacteristic(Joint, j).toJson
        ))
      )
    }

  }

}
