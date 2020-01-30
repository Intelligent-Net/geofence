package net.targetr.geofence

import java.io.RandomAccessFile
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.ByteBuffer

object Run {
  def openData(name: String): (Int, ByteBuffer, RandomAccessFile, Array[Long]) = {
    val idx = Array.fill[Long](24 * 60 + 1)(-1)
    val raf = new RandomAccessFile(GeoRegistryActor.getFn(name), "rw")
    val size = (raf.length / Bffi.entrySize).toInt
    val kv = raf.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, raf.length)

    for (i <- 0 until size) {
      val pos = i * Bffi.entrySize
      val ix = kv.getInt(pos + 4 + 4) / 60

      if (idx(ix) == -1)
        idx(ix) = pos / Bffi.entrySize
    }

    idx(24 * 60) = size

    (size, kv, raf, idx)
  }

  def loadData(name: String, data: String, sampleSize: Double = 0.01, comp: Boolean = false): (Int, ByteBuffer, RandomAccessFile, Array[Long]) = {
    val DateParse = new DateParse()
    val size = using(scala.io.Source.fromInputStream(
               if (comp)
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .length
      }

    // Try to ensure old memory is deallocated
    val dbInstance = GeoRegistryActor.getDb(name)

    if (dbInstance != null) {
      GeoRegistryActor.removeDb(name)

      java.lang.System.gc()
    }

    val idx = Array.fill[Long](24 * 60 + 1)(-1)
    val stem = GeoRegistryActor.getStem(name)
    val raf = new RandomAccessFile("/tmp/" + stem + ".mmf", "rw")
    val kv = raf.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, size * Bffi.entrySize)
    //val kv = ByteBuffer.allocate(size * Bffi.entrySize)
    //val raf = null
    //val kv = ByteBuffer.allocateDirect(size * Bffi.entrySize)

    var i = 0
    using(scala.io.Source.fromInputStream(
               if (comp)
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .filter(str => str(0).isDigit)
                  .map(_.split(","))
                  .map(c => (c(TargetRServer.fields._1).trim.toFloat, c(TargetRServer.fields._2).trim.toFloat, DateParse.string2Second(c(TargetRServer.fields._3).trim)))
                  .toArray
                  .sorted(Ordering.by[(Float,Float,Int), Int](_._3))
                  .map(c => { Bffi.set(kv, idx, i, c._1, c._2, c._3); i += 1 })
                  .length
      }

    idx(24 * 60) = size
    //in.close
    (size, kv, raf, idx)
  }

  def rnd(n: String, p: Double = 100000.0) =
    math.round(n.toDouble * p) / p

  def hourlyData(data: String) = {
    val dh = DayHour()

    using(scala.io.Source.fromInputStream(
               if (data.endsWith(".gz"))
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .drop(1)
                  .map(_.split(","))
                  //.map(c => (c(TargetRServer.fields._1).trim.toFloat, c(TargetRServer.fields._2).trim.toFloat, dh.how(c(TargetRServer.fields._3).trim)))
                  .map(c => (rnd(c(1).trim), rnd(c(2).trim), dh.how(c(0).trim), 1))
                  .toArray
                  .groupMapReduce(a => (a._3, a._1, a._2))(a => a)((x, y) => (y._1, y._2, y._3, x._4 + y._4))
                  .values
      }
  }

  def writeFile[T](filename: String, lines: Array[Array[T]]): Unit = {
    using(new BufferedWriter(new FileWriter(filename))) {
      //bw => lines.foreach(a => bw.write(a._1 + "," + a._2 + "," + a._3 + "," + a._4 + "," + a._5 + "," + a._6 + "," + a._7 + "\n"))
      bw => lines.foreach(a => bw.write(a.mkString(",") + "\n"))
    }
  }

  private def velocity(l: (Double, Double, Long), r: (Double, Double, Long)) = {
    val dist = Circle.haversineDist(l._1, l._2, r._1, r._2)
    val latD = Circle.haversineDist(l._1, l._2, r._1, l._2)
    val lonD = Circle.haversineDist(l._1, l._2, l._1, r._2)
    val secs = l._3
    val dSecs = r._3 - secs
    //val dSecs = if (r._3 - secs == 0) 1 else r._3 - secs
    val vel = dist / dSecs
    val latV = latD / dSecs
    val lonV = lonD / dSecs

    (l._1, l._2, dist, latD, lonD, secs, dSecs, vel, latV, lonV)
  }

  private def acceleration(l: (Double, Double, Double, Double, Double, Long, Long, Double, Double, Double), r: (Double, Double, Double, Double, Double, Long, Long, Double, Double, Double)) = {
    val acc = r._8 - l._8
    val latA = r._9 - l._9
    val lonA = r._10 - l._10

    (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, acc, latA, lonA)
  }

  def eventData(data: String) = {
    val DateParse = new DateParse()
    def grouping(id: String, lat: String, lon: String, t: String) = {
      //(id, rnd(lat), rnd(lon), DateParse.secondEpoch(t))
      (id, lat.toDouble, lon.toDouble, DateParse.secondEpoch(t))
    }

    using(scala.io.Source.fromInputStream(
               if (data.endsWith(".gz"))
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .drop(1)
                  .map(_.split(","))
                  .map(c => grouping(c(8).trim, c(10).trim, c(11).trim, c(0).trim))
                  .toArray
                  .groupMap(_._1)(v => (v._2, v._3, v._4))
                  .map(kv => kv._1 -> kv._2.groupMapReduce(r => (r._1, r._2))(a => a)((a, v) => (v._1, v._2, math.min(a._3, v._3)))
                                           .values
                                           .toArray
                                           .sortBy(_._3)
                                           .sliding(2)
                                           .filter(_.length > 1)
                                           .map(a => velocity(a(0), a(1)))
                                           //.map(a => if (a.length == 1) velocity(a(0), a(0)) else velocity(a(0), a(1)))
                                           .sliding(2)
                                           //.map(a => if (a.length == 1) acceleration(a(0), a(0)) else acceleration(a(0), a(1)))
                                           .map(a => acceleration(a(0), a(1)))
                                           .toArray)
                  //.toArray
      }
  }

  def runCircleTest(size: Int, kv: ByteBuffer, idx: Array[Long], circle: String, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    val c = getCircle(circle)

    runCircleInstance(size, kv, idx, (c._1, c._2), c._3, c._4, subSample, startSec, endSec, duration)
  }

  def runCircleInstance(size: Int, kv: ByteBuffer, idx: Array[Long], center: (Double, Double), distance: Double, unit: String, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runShape(size, kv, idx, Circle.contains(center, distance, unit), subSample, startSec, endSec, duration)
  }

  def runPolygonTest(size: Int, kv: ByteBuffer, idx: Array[Long], polygon: String, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runPolygonInstance(size, kv, idx, getCoords(polygon).toArray, subSample, startSec, endSec, duration)
  }

  def runPolygonInstance(size: Int, kv: ByteBuffer, idx: Array[Long], polygon: Array[(Double,Double)], subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runShape(size, kv, idx, Poly.contains(polygon), subSample, startSec, endSec, duration)
  }

  def runTest(size: Int, kv: ByteBuffer, idx: Array[Long], subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {

    runInstance(size, kv, idx, subSample, startSec, endSec, duration)
  }

  def runInstance(size: Int, kv: ByteBuffer, idx: Array[Long], subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runShape(size, kv, idx, (_: (Double, Double)) => true, subSample, startSec, endSec, duration)
  }

  def runShape(size: Int, kv: ByteBuffer, idx: Array[Long], contains: ((Double, Double)) => Boolean, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60 - 1, duration: Int = 1): (Int, Int) = {

    def timeRange(time: Int): Boolean = 
      time >= startSec && time <= endSec - duration

    if (size == 0)
      (0,0)
    else {
      val mod = if (subSample == 1.0) 1.0 else size / subSample / size
      val lb = idx(startSec / 60).toDouble
      val ub = idx(endSec / 60 + 1).toDouble
      var cnt = 0
      var n = 0

  //val t = time {
      for (i <- Range.BigDecimal(lb, ub, mod).map(_.toDouble)) {
        val p = Bffi.get(kv, i.toInt)

        if (timeRange(p._3)) {
          if (contains((p._1, p._2)))
            cnt += 1

          n += 1
        }
      }
  //}
  //println(t)

      (cnt, n)
    }
  }

  def getCircle(cond: String): (Double, Double, Double, String) = {
    val circle = "\"([^\"]*)\"".r
    val parts = circle.findAllIn(cond).toList.head.replaceAll("\"", "").split("\\s*,\\s*")

    (parts(0).toDouble, parts(1).toDouble, parts(2).toDouble, parts(3))
  }

  def getCoords(cond: String): Seq[(Double, Double)] = {
    val polys = "\"([^\"]*)\"".r
    val arr = polys.findAllIn(cond).flatMap(_.replaceAll("\"","").split("\\s*,\\s*").map(_.toDouble)).toArray

    for (j <- 0 to arr.length if j % 2 == 1)
      yield (arr(j-1),arr(j))
  }

  def using[A <: { def close(): Unit }, B](param: A)(f: A => B): B =
    try {
      f(param)
    }
    finally {
      param.close()
    }

  def time[R](block: => R): Long = {
    val t0 = System.currentTimeMillis()

    block

    System.currentTimeMillis() - t0
  }
}
