package net.targetr.geofence

//#json-support
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val testGeoRangeSampleFormat = jsonFormat7(TestGeoRangeSample)
  implicit val testGeoRangeFormat = jsonFormat6(TestGeoRange)
  implicit val testRangeSampleFormat = jsonFormat6(TestRangeSample)
  implicit val testRangeFormat = jsonFormat5(TestRange)

  implicit val testGeoSlotSampleFormat = jsonFormat5(TestGeoSlotSample)
  implicit val testGeoSlotFormat = jsonFormat4(TestGeoSlot)
  implicit val testSlotSampleFormat = jsonFormat4(TestSlotSample)
  implicit val testSlotFormat = jsonFormat3(TestSlot)

  implicit val testGeoSampleFormat = jsonFormat4(TestGeoSample)
  implicit val testGeoFormat = jsonFormat3(TestGeo)
  implicit val testSampleFormat = jsonFormat3(TestSample)
  implicit val testFormat = jsonFormat2(Test)

  implicit val sizeJsonFormat = jsonFormat1(SampleSize)
  implicit val sampleJsonFormat = jsonFormat1(SubSampleSize)
  implicit val setSampleJsonFormat = jsonFormat2(SetSubSampleSize)
  implicit val dataItemJsonFormat = jsonFormat1(DataItem)
  implicit val dataJsonFormat = jsonFormat1(Data)
  implicit val uploadJsonFormat = jsonFormat3(Uploaded)
  implicit val pointsJsonFormat = jsonFormat4(PointsFound)
}
