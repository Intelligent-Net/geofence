package net.targetr.geofence

import scala.collection.mutable.ArrayBuffer
import scala.util.{Try, Success, Failure}
import scala.util.Random
import java.nio.ByteBuffer
import java.io.RandomAccessFile

object Run {
  /*
  def main(args: Array[String]): Unit = {
    if (args.length <= 2) {
      println("Parameters are : <period csv> <polygon definition> [<sample size>] [<iterations for stability>]")
      System.exit(1)
    }
    val data = args(0)
    if (! new java.io.File(data).isFile) {
      println(s"File does not exist: $data")
      System.exit(1)
    }
    val polygons = args(1)
    if (! new java.io.File(polygons).isFile) {
      println(s"File does not exist: $polygons")
      System.exit(1)
    }
    val sampleSize = if (args.length > 2) args(2).toDouble else 0.01
    val iterations = if (args.length > 3) args(3).toInt else 10

    runPolygon(data, polygons, sampleSize, iterations)
  }
  */

  def loadData(name: String, data: String, sampleSize: Double = 0.01, comp: Boolean = false): (Int, ByteBuffer, RandomAccessFile, Array[Long]) = {
    val size = Json.using(io.Source.fromInputStream(
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
      //dbInstance.idx = null
      //dbInstance.raf = null
      //dbInstance.kv = null
      GeoRegistryActor.removeDb(name)

      java.lang.System.gc
    }

    val idx = Array.fill[Long](24 * 60 + 1)(-1)
    //val stem = GeoRegistryActor.getStem(name)
    //val raf = new RandomAccessFile("/tmp/" + stem + ".mmf", "rw")
    //val kv = raf.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, size * Bffi.entrySize)
    //val kv = ByteBuffer.allocate(size * Bffi.entrySize)
    val raf = null
    val kv = ByteBuffer.allocateDirect(size * Bffi.entrySize)

    var i = 0
    Json.using(io.Source.fromInputStream(
               if (comp)
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .filter(str => str(0).isDigit)
                  .map(_.split(","))
                  .map(c => ((c(TargetRServer.fields(0)).trim.toFloat, c(TargetRServer.fields(1)).trim.toFloat, DateParse.string2Second(c(TargetRServer.fields(2)).trim))))
                  .toArray
                  .sorted(Ordering.by[(Float,Float,Int), Int](_._3))
                  .map(c => { Bffi.set(kv, idx, i, c._1, c._2, c._3); i += 1 })
                  .length
      }

    idx(24 * 60) = size

    //in.close
    (size, kv, raf, idx)
  }

  def loadPolyJson(polygons: String): String = {
    Json.readFile(polygons)
  }

  def runCircleTest(size: Int, kv: ByteBuffer, idx: Array[Long], circle: String, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    val c = Json.getCircle(circle)

    runCircleInstance(size, kv, idx, (c._1, c._2), c._3, c._4, subSample, startSec, endSec, duration)
  }

  def runCircleInstance(size: Int, kv: ByteBuffer, idx: Array[Long], center: (Double, Double), distance: Double, unit: String, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runShape(size, kv, idx, Circle.contains(center, distance, unit), subSample, startSec, endSec, duration)
  }

  def runPolygonTest(size: Int, kv: ByteBuffer, idx: Array[Long], polygon: String, subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runPolygonInstance(size, kv, idx, Json.getCoords(polygon).toArray, subSample, startSec, endSec, duration)
  }

  def runPolygonInstance(size: Int, kv: ByteBuffer, idx: Array[Long], polygon: Array[(Double,Double)], subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runShape(size, kv, idx, Poly.contains(polygon), subSample, startSec, endSec, duration)
  }

  def runTest(size: Int, kv: ByteBuffer, idx: Array[Long], subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {

    runInstance(size, kv, idx, subSample, startSec, endSec, duration)
  }

  def runInstance(size: Int, kv: ByteBuffer, idx: Array[Long], subSample: Double = 1.0, startSec: Int = 0, endSec: Int = 24 * 60 * 60, duration: Int = 1): (Int, Int) = {
    runShape(size, kv, idx, (p : (Double, Double)) => true, subSample, startSec, endSec, duration)
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
      for (i <- lb until ub by mod) {
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

  def time[R](block: => R): Long = {
    val t0 = System.currentTimeMillis()

    block

    System.currentTimeMillis() - t0
  }
}
