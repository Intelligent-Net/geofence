package net.targetr.geofence

object Poly {
  def boundingSquare(polygon: Array[(Double, Double)]): Double = {
    val (minx,miny) = polygon.map(p => (p._1, p._2)).min
    val (maxx,maxy) = polygon.map(p => (p._1, p._2)).max

    (maxx - minx) * (maxy - miny)
  }

  def polyArea(polygon: Array[(Double, Double)]): Double = {
    var area = 0.0
    var j = polygon.length - 1

    for (i <- 0 until polygon.length) {
      area += (polygon(j)._1 + polygon(i)._1) * (polygon(j)._2 - polygon(i)._2)
      //area += polygon(i)._1 * polygon(j)._2 - polygon(i)._2 * polygon(j)._1
      j = i
    }

    area / 2.0
  }

  def containsPoints(polygons: Array[(Double, Double)], lat: Double, lon: Double): Boolean = {
    contains(polygons)((lat, lon))
  }

  // Returns true if the point p lies inside the polygon[] with n vertices
  // Note: not pure function internally but has no side effects
  def contains(polygon: Array[(Double, Double)])(p: (Double, Double)): Boolean = {
    // There must be at least 3 vertices in polygon
    val n = polygon.length

    assert(n >= 3)

    // Create a point for line segment from p to infinite
    val refPoint = (Double.MaxValue, p._2)

    // Count intersections of the above line with sides of polygon
    var count = 0
    var i = 0;

    do {
      val next = (i + 1) % n;

      // Check if the line segment from 'p' to 'ref(Double, Double)' intersects
      // with the line segment from 'polygon[i]' to 'polygon[next]'
      if (doIntersect(polygon(i), polygon(next), p, refPoint)) {
        // If the point 'p' is colinear with line segment 'i-next',
        // then check if it lies on segment. If it does, return true,
        // otherwise false
        if (orientation(polygon(i), p, polygon(next)) == 0)
           return onSegment(polygon(i), p, polygon(next));

        count += 1;
      }

      i = next;
    }
    while (i != 0)

    // Return true if count is odd, false otherwise
    count % 2 == 1
  }

  // The function that returns true if line segment 'p1q1'
  // and 'p2q2' intersect.
  private def doIntersect(p1: (Double, Double), q1: (Double, Double), p2: (Double, Double), q2: (Double, Double)): Boolean = {
    // Find the four orientations
    val o1 = orientation(p1, q1, p2)
    val o2 = orientation(p1, q1, q2)
    val o3 = orientation(p2, q2, p1)
    val o4 = orientation(p2, q2, q1)

    // General case
    if (o1 != o2 && o3 != o4)
      true
    else {
      // Special Cases
      // p1, q1 and p2 are colinear and p2 lies on segment p1q1
      (o1 == 0 && onSegment(p1, p2, q1)) ||
      (o2 == 0 && onSegment(p1, q2, q1)) ||
      (o3 == 0 && onSegment(p2, p1, q2)) ||
      (o4 == 0 && onSegment(p2, q1, q2))
    }
  }

  // Given three points p, q, r, check if point q lies on line segment pr
  private def onSegment(p: (Double, Double), q: (Double, Double), r: (Double, Double)): Boolean = {
    q._1 <= Math.max(p._1, r._1) &&
    q._1 >= Math.min(p._1, r._1) &&
    q._2 <= Math.max(p._2, r._2) &&
    q._2 >= Math.min(p._2, r._2)
  }

  // To find orientation of ordered triplet (p, q, r).
  // Returns
  // 0 --> p, q and r are colinear
  // 1 --> Clockwise
  // 2 --> Anticlockwise
  private def orientation(p: (Double, Double), q: (Double, Double), r: (Double, Double)): Int  = {
    val v = (q._2 - p._2) * (r._1 - q._1) - (q._1 - p._1) * (r._2 - q._2)

    if (v == 0) 0  // colinear
    else if (v > 0) 1
    else 2
  }
//}

/*
//object Poly {
  def main(args: Array[String]): Unit = {
    val p1 = Array((0.0, 0.0), (10.0, 0.0), (10.0, 10.0), (0.0, 10.0))

    assert(! contains(p1, (20.0, 20.0)))
    assert(contains(p1, (5.0, 5.0)))

    val p2 = Array((0.0, 0.0), (5.0, 5.0), (5.0, 0.0))

    assert(contains(p2, (3.0, 3.0)))
    assert(contains(p2, (5.0, 1.0)))
    assert(! contains(p2, (8.0, 1.0)))

    val p3 =  Array((0.0, 0.0), (10.0, 0.0), (10.0, 10.0), (0.0, 10.0))

    assert(! contains(p3, (-1.0, 10.0)))

    val p4 =  Array((1.0, 3.0), (2.0, 8.0), (5.0, 4.0), (5.0, 9.0), (7.0, 5.0), (6.0, 1.0), (3.0, 1.0))

    assert(contains(p4, (5.5, 7.0)))
    assert(! contains(p4, (4.5, 7.0)))

    val p5 = Array((42.499148, 27.485196),
                   (42.498600, 27.480000),
                   (42.503800, 27.474680),
                   (42.510000, 27.468270),
                   (42.510788, 27.466904),
                   (42.512116, 27.465350),
                   (42.512000, 27.467000),
                   (42.513579, 27.471027),
                   (42.512938, 27.472668),
                   (42.511829, 27.474922),
                   (42.507945, 27.480124),
                   (42.509082, 27.482892),
                   (42.536026, 27.490519),
                   (42.534470, 27.499703),
                   (42.499148, 27.485196))

    assert(contains(p5, (42.508956, 27.483328)))
    assert(contains(p5, (42.505, 27.48)))

    val p6 = Array((-1.0, -1.0), (-1.0, 1.0), (1.0, 1.0), (1.0, -1.0))

    assert(contains(p6, (0.0, 1.0)))
    assert(contains(p6, (-1.0, 0.0)))
    assert(! contains(p6, (-1.0, 1.00001)))

    val p7 = Array( (0.0, 0.0), (0.0, 1.0), (1.0, 2.0), (1.0, 99.0), (100.0, 0.0))

    assert(contains(p7, (3.0, 4.0)))
    assert(contains(p7, (3.0, 4.1)))
    assert(contains(p7, (3.0, 3.9)))
  }
*/
}
