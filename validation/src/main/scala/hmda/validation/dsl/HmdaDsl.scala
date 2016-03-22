package hmda.validation.dsl

import java.text.SimpleDateFormat

trait HmdaDsl {
  def validTimestampFormat: Predicate[String] = new Predicate[String] {
    override def validate: String => Boolean = {
      checkDateFormat(_)
    }
    override def failure: String = s"invalid timestamp format"
  }

  def checkDateFormat[T](s: String): Boolean = {
    try {
      val format = new SimpleDateFormat("yyyyMMddHHmm")
      format.setLenient(false)
      format.parse(s)
      true
    } catch {
      case e: Exception => false
    }
  }
}
