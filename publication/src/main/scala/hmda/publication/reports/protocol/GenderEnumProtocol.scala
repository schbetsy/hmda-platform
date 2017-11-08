package hmda.publication.reports.protocol

import hmda.model.publication.reports.GenderEnum
import hmda.model.publication.reports.GenderEnum._
import spray.json.{ DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat }

trait GenderEnumProtocol extends DefaultJsonProtocol {

  implicit object GenderEnumFormat extends RootJsonFormat[GenderEnum] {

    override def write(obj: GenderEnum): JsValue = JsString(obj.description)

    override def read(json: JsValue): GenderEnum = json match {
      case JsString(description) => description match {
        case Male.description => Male
        case Female.description => Female
        case Joint.description => Joint
        case _ => throw DeserializationException(s"Unable to translate JSON string into valid Gender value: $description")
      }

      case _ => throw DeserializationException("Unable to deserialize")
    }

  }
}
