package net.targetr.geofence

object Circle {
  // AVERAGE_RADIUS_OF_EARTH = 3443.89849 - Nautical miles
  // NAUTICAL_MILES_TO_KILOMETERS = 1.85199074074
  // NAUTICAL_MILES_TO_MILES = 1.150787037037
  private final val earthRadiusMeters = 6378137.0
  private final val earthRadiusYards = 6378137.0 * 1.09361
  private final val earthRadiusMiles = 3443.89849 * 1.150787037037
  private final val earthRadiusKilometers = 3443.89849 * 1.85199074074

  def haversineDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double, radius: Double = earthRadiusMeters): Double = {
    val dLat2 = math.toRadians(lat2 - lat1) / 2.0
    val dLon2 = math.toRadians(lon2 - lon1) / 2.0
    val a = math.sin(dLat2) * math.sin(dLat2) +
            math.sin(dLon2) * math.sin(dLon2) *
            math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2))

    radius * 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
  }

  // Returns true if the point p lies inside the Circle
  def contains(center: (Double, Double), radius: Double, units: String = "k")(p: (Double, Double)): Boolean = units.take(2).toLowerCase match {
    case "0" | "k" | "km" | "ki" => haversineDist(center._1, center._2, p._1, p._2, earthRadiusKilometers) < radius
    case "1" | "mi" | "ml" => haversineDist(center._1, center._2, p._1, p._2, earthRadiusMiles) < radius
    case "2" | "m" | "me" => haversineDist(center._1, center._2, p._1, p._2, earthRadiusMeters) < radius
    case "3" | "y" | "yd" | "ya" => haversineDist(center._1, center._2, p._1, p._2, earthRadiusYards) < radius
    case _ => false
  }
//}

/*
  def main(args: Array[String]): Unit = {
    assert(contains((40.75561,-73.95228), 5000, (40.77476,-73.99824), "Meters"))
    assert(! contains((40.75561,-73.95228), 4000, (40.77476,-73.99824), "Meters"))

    assert(contains((40.75561,-73.95228), 5000, (40.77476,-73.99824), "yards"))
    assert(! contains((40.75561,-73.95228), 4000, (40.77476,-73.99824), "yards"))

    assert(contains((40.75561,-73.95228), 5, (40.77476,-73.99824), "km"))
    assert(! contains((40.75561,-73.95228), 4, (40.77476,-73.99824), "km"))

    assert(contains((40.75561,-73.95228), 3, (40.77476,-73.99824), "mi"))
    assert(! contains((40.75561,-73.95228), 2, (40.77476,-73.99824), "mi"))
  }
*/
}
