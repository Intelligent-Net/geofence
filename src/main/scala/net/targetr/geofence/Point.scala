package net.targetr.geofence

case class Point(x: Float, y: Float, MULT: Long = 100000) {
  private val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"

  def compress(p: Array[Point]) = {
    def stringify(index: Long): String = {
      val remainder = index & 31
      val ind = (index - remainder) / 32
      val rem = if (ind > 0) remainder + 32 else remainder

      CHARS.charAt(rem.toInt) + (if (ind > 0) stringify(ind) else "")
    }

    ((0, 0) +: p.map(p => (math.round(p.x * MULT), math.round(p.y * MULT))))
      .sliding(2)
      .map(ps => (ps(1)._1 - ps(0)._1, ps(1)._2 - ps(0)._2))
      .map(d => (((d._2 << 1) ^ (d._2 >> 31)).toLong, ((d._1 << 1) ^ (d._1 >> 31)).toLong))
      .map(d => ((d._1 + d._2) * (d._1 + d._2 + 1) / 2) + d._2)
      .map(d => stringify(d))
      .reduce((a, v) => a + v)
  }

  def decompress(c: String) = {
    val pa = scala.collection.mutable.ArrayBuffer.empty[List[Long]]
    var p = scala.collection.mutable.ArrayBuffer.empty[Long]

    // Local side effects
    c.toArray
     .map(CHARS.indexOf(_))
     .map(n => if (n < 32) { p += n; pa += p.toList; p = scala.collection.mutable.ArrayBuffer.empty[Long] } else p += n - 32)

    pa.map(_.reverse
            .reduce((r, n) => if (r == 0) n else r * 32 + n))
      .map(r => (r, ((math.sqrt(8 * r + 5) - 1) / 2).toLong))
      .map(rd => (rd._1 - rd._2 * (rd._2 + 1.0) / 2.0, rd._2))
      .map(yd => (yd._1, yd._2 - yd._1))
      .map(xy => (if ((xy._1 % 2) == 1) -(xy._1 + 1) else xy._1,
                  if ((xy._2 % 2) == 1) -(xy._2 + 1) else xy._2))
      .map(xy => (xy._1 / 2, xy._2 / 2))
      .scan((0.0,0.0))((a, v) => (a._1 + v._1, a._2 + v._2))
      .drop(1)
      .map(xy => Point((xy._1 / MULT).toFloat, (xy._2 / MULT).toFloat))
      .toList
  }

  override def toString(): String =
    s"Point{x=$x, y=$y}"
}
