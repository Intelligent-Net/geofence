package net.targetr.geofence

import java.time.format.DateTimeFormatter
import java.time.Instant

class DateParse(dateFormat: String = "yyyy-MM-dd HH:mm:ss zzz") {
  private val DATE = DateTimeFormatter.ofPattern(dateFormat)

  def string2Date(s: String): Instant =
    Instant.from(DATE.parse(s + " UTC"))

  def string2Time(s: String): Instant =
    Instant.from(DATE.parse("2020-01-01 " + s + " UTC"))

  def string2Long(s: String): Long =
    string2Date(s).toEpochMilli

  def secondEpoch(s: String): Long =
    string2Date(s).toEpochMilli / 1000

  def minuteEpoch(s: String): Long =
    string2Date(s).toEpochMilli / 1000 / 60

  def time2Long(s: String): Long =
    string2Time(s).toEpochMilli

  def string2Minute(s: String): Int =
    ((string2Long(s) / (1000 * 60)) % (24 * 60)).toInt

  def string2Second(s: String): Int =
    ((string2Long(s) / 1000) % (24 * 60 * 60)).toInt

  def time2Minute(s: String): Int =
    ((time2Long(s) / (1000 * 60)) % (24 * 60)).toInt

  def time2Second(s: String): Int =
    ((time2Long(s) / 1000) % (24 * 60 * 60)).toInt
}
