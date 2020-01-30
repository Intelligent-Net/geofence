package net.targetr.geofence

import java.io.RandomAccessFile
import java.io.FileWriter
import java.io.BufferedWriter
import java.nio.ByteBuffer

//import scala.util.parsing.json._
import scala.util.matching.Regex._

object IoT {
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

  def iotData(data: String, cd: Int = 3302, v: String = "5500", f: (Any,String) => Any = (m: Any, v: String) => m.asInstanceOf[Map[String,Any]](v)) = {
    import org.json4s.JsonDSL._
    import org.json4s._
    import org.json4s.native.JsonMethods._
    import scala.util.hashing.MurmurHash3
    implicit val formats = DefaultFormats
    val dev = """"((?>..-){7}..)"""".r
    val ver = """"version":"([^"]+)"""".r
    val code = """"ipso":(\{.+?\}\})""".r
    val tim = """\$date":"([^"]+)"""".r
    val DateParse = new DateParse("yyyy-MM-dd'T'HH:mm:ss zzz")
    def re(l: String) = {
      val device =
        MurmurHash3.stringHash(
        dev.findFirstMatchIn(l) match {
          //case Some(data: Match) => data.group(1) + data.group(3) + data.group(2)
          case Some(data: Match) => data.group(1)
          case None => ""
        })
      val ipso =
        code.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1)
          case None => ""
        }
      val time =
        tim.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1)
          case None => ""
        }
      val version =
        ver.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1)
          case None => "0.2.3"
        }

      val m = parse(ipso).extract[Map[Int, Any]]

      //(device, m.map(_.asInstanceOf[Map[Int,Boolean]]), DateParse.secondEpoch(time.substring(0,19)))
      //(device, m(cd).asInstanceOf[Map[Int,Any]](5500).asInstanceOf[Boolean], DateParse.secondEpoch(time.substring(0,19)))
      //(device, m(cd).asInstanceOf[Map[String,Any]].filter(_._1 == "5500"), DateParse.secondEpoch(time.substring(0,19)))
      //def find(m: Any): Boolean =
      //  m.asInstanceOf[Map[String,Any]](v).asInstanceOf[Boolean]
      //(device, m(cd).asInstanceOf[Map[String,Any]]("5500").asInstanceOf[Boolean], DateParse.secondEpoch(time.substring(0,19)))
      //(device, find(m(cd), "5500"), DateParse.secondEpoch(time.substring(0,19)))
      var res = f(m(cd), v)

      if (res.getClass.toString.contains("oolean")) {
        if (cd == 3302)
          res = if (version != "0.2.3") res else ! res.asInstanceOf[Boolean]
        res = if (res.asInstanceOf[Boolean]) 1.0 else 0.0
      }

      (device, res, DateParse.secondEpoch(time.substring(0,19)))
    }

    using(scala.io.Source.fromInputStream(
               if (data.endsWith(".gz"))
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .map(re(_))
                  .toArray
                  .filter(_._1 != 0)
                  .sortBy(a => (a._1, a._3))
                  //.groupMap(_._1)(v => (v._3, v._2))
                  //.map(kv => kv._1 -> kv._2.sortBy(_._1))
      }
  }

  def getLocation(fn: String, pos: Int = 0) = {
    import scala.util.hashing.MurmurHash3
    using(scala.io.Source.fromInputStream(
               if (fn.endsWith(".gz"))
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(fn))
               else
                 new java.io.FileInputStream(fn))
               ) {
        src => src.getLines
                  .drop(1)
                  .map(_.split(","))
                  .toArray
                  .groupMapReduce(s => MurmurHash3.stringHash(s(pos)))(v => v.filter(_ != v(pos)))((_, v) => v)
      }
  }

  def writeFile[T](filename: String, lines: Array[Array[T]]): Unit = {
    using(new BufferedWriter(new FileWriter(filename))) {
      //bw => lines.foreach(a => bw.write(a._1 + "," + a._2 + "," + a._3 + "," + a._4 + "," + a._5 + "," + a._6 + "," + a._7 + "\n"))
      bw => lines.foreach(a => bw.write(a.mkString(",") + "\n"))
    }
  }

  def writeIOT(fn: String, data: String, sense: String, cd: Int = 3302, v: String = "5500", f: (Any,String) => Any = (m: Any, v: String) => m.asInstanceOf[Map[String,Any]](v)) = {
    import scala.util.hashing.MurmurHash3

    /*
    def writeFile(filename: String, lines: Seq[(Any,Any,Any)]): Unit = {
      using(new BufferedWriter(new FileWriter(filename))) {
        bw => lines.foreach(a => bw.write(a._1 + "," + a._2 + "," + a._3 + "\n"))
      }
    }
    */
    //def writeFile(filename: String, lines: Seq[(Any,Any,Any,Any,Any,Any,Any)]): Unit = {
    val df = iotData(data, cd, v, f)
    val sensors = getLocation(sense)
      /*
      using(scala.io.Source.fromInputStream(
                 if (sense.endsWith(".gz"))
                   new java.util.zip.GZIPInputStream(new java.io.FileInputStream(sense))
                 else
                   new java.io.FileInputStream(sense))
                 ) {
          src => src.getLines
                    .drop(1)
                    .map(_.split(","))
                    .toArray
                    .groupMapReduce(s => MurmurHash3.stringHash(s(4)))(a => (a(0), a(1), a(2), a(3), a(4)))((_, v) => v)
        }
        */

    //sensors//.map(_._1)
    //println(sensors)
    //val joined = df.filter(a => sensors.contains(a._1))
    //               .map(a => (a._1, a._2, a._3, sensors(a._1)._1, sensors(a._1)._2, sensors(a._1)._3, sensors(a._1)._4))
    //val joined = df.filter(a => sensors.contains(a._1))
    //               .map(a => Array(a._1, a._2, a._3, sensors(a._1)(0), sensors(a._1)(1), sensors(a._1)(2), sensors(a._1)(3)))
    val joined = df.map(a => Array(a._1, a._2, a._3))
    //joined

    writeFile(fn, joined)
  }

  def sensorEvents(data: String) = {
    var lst = 0L
    var prv = -1.0

    using(scala.io.Source.fromInputStream(
               if (data.endsWith(".gz"))
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .map(_.split(","))
                  .toArray
                  .map(v => (v(0).toLong, v(1).toDouble, v(2).toLong))
                  .groupMap(_._1)(v => (v._2, if (lst == 0 || prv != v._2) { lst = v._3; prv = v._2; lst } else lst))
                  .map(a => a._1 -> a._2.distinct.sortBy(_._2))
      }
  }

  //def sensorHours(data: String, locs: String, pos: Int = 0) = {
  def sensorHours(data: String) = {
    val df = sensorEvents(data)
    //val mi = df.values.flatten.reduce((a, v) => (v._1, math.min(a._2, v._2)))._2
    //val ma = df.values.flatten.reduce((a, v) => (v._1, math.max(a._2, v._2)))._2

    //val ih = df.map(a => a._2.map(b => b._2 / 60 / 60 -> a._1)).flatten.toSet
    //val cp = for {a <- (mi / 60 / 60 to ma / 60 / 60); b <- df.keys} yield (a,b)

    def avg(v: Array[(Double, Long)]) = {
      def summer(l: Array[(Double, Double)]) = {
        ((0.0,0.0) +: l.sliding(2).map(v => (v(0)._1, v(1)._2))
                                  .toList :+ (l.last._1, 1.0))
          .sliding(2)
          .map(v => v(1)._1 * (v(1)._2 - v(0)._2))
          .sum
      }

      v.groupMap(_._2 / 60 / 60)(a => (a._1, (a._2 % (60 * 60)) / 60.0 / 60.0))
       .toArray
       .sortBy(_._1)
       .sliding(2)
       .map(a => (a(0)._1.toInt, summer(a(0)._2.max +: a(1)._2)))
    }

    //val sense = getLocation(locs, pos)

    df.filter(_._2.size > 1)
      .map(a => a._1 -> avg(a._2).toArray)
  }

  def rmse(y: Array[Double], py: Array[Double]) = {
    val (soe,cnt) = y.zip(py).map{case (y, py) => (math.pow(y - py, 2), 1)}.reduce((a, v) => (a._1 + v._1, a._2 + v._2))

    math.sqrt(soe / cnt)
  }

  // fill in any gaps
  def gapper(data: Array[Array[Int]]) = {
    def gapper2(data: Array[Array[Int]]) = {
      val sms = data.groupMapReduce(a => (a(2),a(3)))(_(4))((a,v) => math.max(a,v))

      data.map(a => (a(2),a(3))).flatMap(a => for (h <- 0 to 23; d <- 0 to 6; i <- 0 until sms(a)) yield List(h,d,a._1,a._2,i))
    }

    def gapper3(data: Array[Array[Int]]) = {
      val sms = data.groupMapReduce(a => (a(2),a(3),a(4)))(_(4))((a,v) => math.max(a,v))

      data.map(a => (a(2),a(3),a(4))).flatMap(a => for (h <- 0 to 23; d <- 0 to 6; i <- 0 until sms(a)) yield List(h,d,a._1,a._2,a._3,i))
    }

    data(0).length match {
      case 5 => gapper2(data)
      case 6 => gapper3(data)
    }
  }

  def model(data: String, sensors: String) = {
    import smile.math.MathEx;
    import smile.data.DataFrame;
    import smile.data.Tuple;
    import smile.data.formula.Formula
    import smile.regression._

    val sense = getLocation(sensors, 0).map(a => a._1 -> a._2.map(d => if (! Character.isDigit(d(0))) d.substring(1).toDouble else d.toDouble))
    val readings = sensorHours(data)

    val all = readings.filter(a => sense.contains(a._1.toInt)).map(a => a._2.map(v => (v._1 % 24).toDouble +: (v._1 / 24 % 7).toDouble +: sense(a._1.toInt) :+ v._2)).flatten.toArray
    val y = all.map(_(6))
    //val bank = all.groupMapReduce(a => (a(0), a(1), a(3), a(4)))(a => (a(6),1)){case ((aa,av),(va,vv)) => (aa + va, av + vv)}.view.mapValues{case (s, c) => s / c}.toArray.map{case ((hr,dow,flr,bk), v) =>  Array(hr, dow, flr, bk, v)}
    val desk = all.groupMapReduce(a => (a(0), a(1), a(3), a(4), a(5)))(a => (a(6),1)){case ((aa,av),(va,vv)) => (aa + va, av + vv)}.view.mapValues{case (s, c) => s / c}.toArray.map{case ((hr,dow,flr,bk,dk), v) =>  Array(hr, dow, flr, bk, dk, v)}
    //val bf = DataFrame.of(bank, "hour", "dow", "floor", "bank", "util")
    val df = DataFrame.of(desk, "hour", "dow", "floor", "bank", "desk", "util")
    val formula = new Formula("util")
    //val rf = randomForest(formula, df)
    //val rf = gbm(formula, df)
    val rf = lasso(formula, df, 0.1)
    val py = rf.predict(df)

    //rmse(y, py)
    val di = desk.map(vs => vs.dropRight(1)).map(_.map(_.toInt))
    val every = gapper(di).map(vs => (vs(0),vs(1),vs(2),vs(3),vs(4))).toSet
    val gMap = desk.map(vs => (vs(0).toInt,vs(1).toInt,vs(2).toInt,vs(3).toInt,vs(4).toInt) -> vs(5)).toMap
    every.map(v => v -> {val k = Array(v._1.toDouble,v._2.toDouble,v._3.toDouble,v._4.toDouble,v._5.toDouble); val py = rf.predict(k); (gMap.getOrElse(v,0.0), py)}).map{case (k,v) => k.productIterator.map(_.asInstanceOf[Int]).toArray ++ v.productIterator.map(_.asInstanceOf[Double]).toArray}.toArray
  }

  def busData(data: String) = {
    val lat = """lat":([^,}]+),""".r
    val lon = """lng":([^,}]+),""".r
    val tim = """.ime":"([^,}]+)",""".r
    //val dev = """"(?>device_id|id)":([^,}]+),""".r
    //val dev = """"device_id":([^,}]+),""".r
    //val dev = """"((?>..-){7}(?>..))"""".r
    //val dev = """"((?>..){4}-(?>..){4})"""".r
    val dev = """"(..)((?>-..){3})((?>-..){4})"""".r
    val DateParse = new DateParse("yyyy-MM-dd'T'HH:mm:ss zzz")
    def re(l: String) = {
      val device =
        dev.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1) + data.group(3) + data.group(2)
          case None => ""
        }
      val latitude =
        lat.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1)
          case None => ""
        }
      val longitude =
        lon.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1)
          case None => ""
        }
      val time =
        tim.findFirstMatchIn(l) match {
          case Some(data: Match) => data.group(1)
          case None => ""
        }
      (device, latitude.toDouble, longitude.toDouble, DateParse.secondEpoch(time.substring(0,19)))
    }

    using(scala.io.Source.fromInputStream(
               if (data.endsWith(".gz"))
                 new java.util.zip.GZIPInputStream(new java.io.FileInputStream(data))
               else
                 new java.io.FileInputStream(data))
               ) {
        src => src.getLines
                  .map(re(_))
                  .toArray
                  .filter(_._1 != "")
                  .groupMap(_._1)(v => (v._2, v._3, v._4))
                  .map(kv => kv._1 -> kv._2.groupMapReduce(r => (r._1, r._2))(a => a)((a, v) => (v._1, v._2, math.min(a._3, v._3)))
                                           .values
                                           .toArray
                                           .sortBy(_._3)
                                           //.sliding(2)
                                           //.filter(_.length > 1)
                                           //.map(a => velocity(a(0), a(1)))
                                           //.sliding(2)
                                           //.map(a => acceleration(a(0), a(1)))
                                           .toArray)
                  //.toArray
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
