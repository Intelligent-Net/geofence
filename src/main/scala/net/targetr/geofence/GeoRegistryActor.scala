package net.targetr.geofence

import akka.actor.{ Actor, ActorLogging, Props }
import scala.util.{Try, Success, Failure}
import java.nio.ByteBuffer
import java.io.RandomAccessFile

final case class Sample(size: Double)
final case class SetSample(size: Double)
final case class SubSample(areaId: String)
final case class SetSubSample(areaId: String, sample: Double)

final case class TestRange(areaId: String, itemId: String, start: Int, end: Int, duration: Int)
final case class TestRangeSample(areaId: String, itemId: String, start: Int, end: Int, duration: Int, sample: Double)
final case class TestGeoRange(areaId: String, itemId: String, condition: String, start: Int, end: Int, duration: Int)
final case class TestGeoRangeSample(areaId: String, itemId: String, condition: String, start: Int, end: Int, duration: Int, sample: Double)
final case class TestSlot(areaId: String, itemId: String, slot: String)
final case class TestSlotSample(areaId: String, itemId: String, slot: String, sample: Double)
final case class TestGeoSlot(areaId: String, itemId: String, condition: String, slot: String)
final case class TestGeoSlotSample(areaId: String, itemId: String, condition: String, slot: String, sample: Double)
final case class Test(areaId: String, itemId: String)
final case class TestSample(areaId: String, itemId: String, sample: Double)
final case class TestGeo(areaId: String, itemId: String, condition: String)
final case class TestGeoSample(areaId: String, itemId: String, condition: String, sample: Double)

final case class PointsFound(found: Int, total: Int, success: Boolean = true, message: String = "OK")
final case class Uploaded(total: Int, success: Boolean, filename: String)
final case class Opened(total: Int, success: Boolean, filename: String)

object GeoRegistryActor {
  final case class LoadPolyFile(file: String, name: String)
  final case class OpenPolyFile(name: String)

  final case class RunTestRange(geo: TestRange)
  final case class RunTestRangeSample(geo: TestRangeSample)
  final case class RunTestGeoRange(geo: TestGeoRange)
  final case class RunTestGeoRangeSample(geo: TestGeoRangeSample)

  final case class RunTestSlot(geo: TestSlot)
  final case class RunTestSlotSample(geo: TestSlotSample)
  final case class RunTestGeoSlot(geo: TestGeoSlot)
  final case class RunTestGeoSlotSample(geo: TestGeoSlotSample)

  final case class RunTest(geo: Test)
  final case class RunTestSample(geo: TestSample)
  final case class RunTestGeo(geo: TestGeo)
  final case class RunTestGeoSample(geo: TestGeoSample)

  final case object GetSample
  final case class GetSubSample(id: String)

  def props: Props = Props[GeoRegistryActor]

  // State Data
  private var sample = 1.0 // Does not change at present
  private val db = new LRUCache[String, DataBase](8) // small for now

  def getStem(i: String): String =
    i.split("\\.")(0).split("[\\\\/]").reverse(0)

  def getFn(name: String) = {
    "/tmp/" + getStem(name) + ".mmf"
  }

  def getDb(i: String): DataBase =
    db.get(getStem(i))

  def removeDb(i: String): DataBase =
    db.remove(getStem(i))
}

case class DataBase(idx: Array[Long],
                    data: ByteBuffer,
                    raf: RandomAccessFile,
                    size: Int,
                    var subSample: Double = 1.0)

class GeoRegistryActor extends Actor with ActorLogging {
  import GeoRegistryActor._

  private def testShapeSlot(id: String, sample: Double, condition: String = "", slot: String = "00:00:00,23:59:59,0"): Unit = {
    def default(v: String, d: String) =
      if (v.isEmpty) d else v

    var start = 0
    var end = 24 * 60 * 60 - 1
    var duration = 0
    val DateParse = new DateParse()

    Try {
      val range = (slot + ",,0").split("\\s*,\\s*")

      start = DateParse.time2Second(default(range(0), "00:00:00"))
      end = DateParse.time2Second(default(range(1), "23:59:59"))
      duration = default(range(2), "0").toInt
    } match {
      case Success(_) =>
        testShape(id, sample, condition, start, end, duration)
      case Failure(e) =>
        sender() ! PointsFound(0, 0, success = false, e.toString)
    }
  }

  private def testShape(id: String, sample: Double, condition: String = "", startSec: Int = 0, endSec: Int = 24 * 60 * 60 - 1, duration: Int = 0): Unit = {
    def testResponse(found: Int, total: Int): Unit = {
      if (total == 0)
        sender() ! PointsFound(found, total, success = false, "No Data Loaded")
      else if (found == 0)
        sender() ! PointsFound(found, total, success = true, "No points found")
      else
        sender() ! PointsFound(found, total)
    }

    Try {
      val i = db.get(id)

      if (i != null)
      {
        val localSample = if (sample == 0.0) i.subSample else sample

        if (condition == "")
          Run.runTest(i.size, i.data, i.idx, localSample, startSec, endSec, duration)
        else if (condition.contains("gpsInsideCircle"))
          Run.runCircleTest(i.size, i.data, i.idx, condition, localSample, startSec, endSec, duration)
        else 
          Run.runPolygonTest(i.size, i.data, i.idx, condition, localSample, startSec, endSec, duration)
      }
      else
        throw new NullPointerException(s"$id data not loaded")
    } match {
      case Success(v) => 
        testResponse(v._1, v._2)
      case Failure(e) =>
        sender() ! PointsFound(0, 0, success = false, e.toString)
    }
  }

  private def uploading(name: String, file: String, sample: Double, compressed: Boolean): Unit = {
    Try(Run.loadData(name, file, sample, compressed)) match {
      case Success(v) => 
        db.put(getStem(name), DataBase(size = v._1, data = v._2, raf = v._3, idx = v._4))

        new java.io.File(file).delete
        sender() ! Uploaded(v._1, success = true, name)
      case Failure(e) =>
        sender() ! Uploaded(0, success = false, e.toString)
    }
  }

  private def uploadMMF(name: String, file: String, compressed: Boolean): Unit = {
    //Try(Run.loadData(name, file, sample, compressed)) match {
    Try(Run.openData(name)) match {
      case Success(v) => 
        db.put(getStem(name), DataBase(size = v._1, data = v._2, raf = v._3, idx = v._4))

        new java.io.File(file).delete
        sender() ! Uploaded(v._1, success = true, name)
      case Failure(e) =>
        sender() ! Uploaded(0, success = false, e.toString)
    }
  }

  private def opening(name: String): Unit = {
    Try(Run.openData(name)) match {
      case Success(v) => 
        db.put(getStem(name), DataBase(size = v._1, data = v._2, raf = v._3, idx = v._4))

        sender() ! Opened(v._1, success = true, name)
      case Failure(e) =>
        sender() ! Opened(0, success = false, e.toString)
    }
  }

  def receive: Receive = {
    case Sample(samplePara) =>
      sample = samplePara
      sender() ! Sample(samplePara)
    case GetSample =>
      sender() ! Sample(sample)
    case SetSubSample(id, subSamplePara) =>
      val i = db.get(id)
      if (i != null) {
        i.subSample = subSamplePara
        sender() ! SetSubSample(id, subSamplePara)
      }
      else
        sender() ! SetSubSample(id, 0.0)
    case SubSample(id) =>
      val i = db.get(id)
      if (i != null) {
        sender() ! SetSubSample(id, i.subSample)
      }
      else
        sender() ! SetSubSample(id, 0.0)
    case LoadPolyFile(file, name) =>
      val ext = name.substring(name.lastIndexOf(".") + 1)

      if (ext == "csv") {
        uploading(name, file, sample, compressed = false)
      }
      else if (ext == "gz" && name.endsWith(".csv.gz")) {
        uploading(name, file, sample, compressed = true)
      }
      else if (name.endsWith(".mmf")) {
        new java.io.File(file).renameTo(new java.io.File(getFn(name)))
        println("File uploaded requested : " + name)
        sender() ! Uploaded(0, success = true, name)
      }
      else {
        println("Unknown file upload requested : " + ext)
        sender() ! Uploaded(0, success = false, name)
      }
    case OpenPolyFile(name) =>
      opening(name)
    case RunTestGeoRangeSample(s) =>
      testShape(s.areaId, s.sample, s.condition, s.start, s.end, s.duration)
    case RunTestGeoRange(s) =>
      testShape(s.areaId, 0.0, s.condition, s.start, s.end, s.duration)
    case RunTestRangeSample(s) =>
      testShape(s.areaId, s.sample, "", s.start, s.end, s.duration)
    case RunTestRange(s) =>
      testShape(s.areaId, 0.0, "", s.start, s.end, s.duration)
    case RunTestGeoSlotSample(s) =>
      testShapeSlot(s.areaId, s.sample, s.condition, s.slot)
    case RunTestGeoSlot(s) =>
      testShapeSlot(s.areaId, 0.0, s.condition, s.slot)
    case RunTestSlotSample(s) =>
      testShapeSlot(s.areaId, s.sample, "", s.slot)
    case RunTestSlot(s) =>
      testShapeSlot(s.areaId, 0.0, "", s.slot)
    case RunTestGeoSample(s) =>
      testShape(s.areaId, s.sample, s.condition)
    case RunTestGeo(s) =>
      testShape(s.areaId, 0.0, s.condition)
    case RunTestSample(s) =>
      testShape(s.areaId, s.sample)
    case RunTest(s) =>
      testShape(s.areaId, 0.0)
    case e => sender() ! Uploaded(0, success = false, e.toString)
  }
}
