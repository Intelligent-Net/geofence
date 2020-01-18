package net.targetr.geofence

import java.time._
import java.time.format.DateTimeFormatter

case class DayHour(pattern: String = "yyyy-MM-dd HH:mm:ss") {
  private val dow = Map("Sun" -> 0, "Mon" -> 1, "Tue" -> 2, "Wed" -> 3, "Thu" -> 4, "Fri" -> 5, "Sat" -> 6)
  private val fmt = DateTimeFormatter.ofPattern(pattern)
  private val conv = DateTimeFormatter.ofPattern("EHH")

  def how(t: String): Long = {
    val ts = conv.format(fmt.parse(t))

    dow(ts.substring(0, 3)) * 24 + ts.substring(3).toLong
  }

  def hod(t: String) =
    how(t) % 24

  def dh(t: String) = {
    val tt = how(t)

    (tt / 24, tt % 24)
  }
}
