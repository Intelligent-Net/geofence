package net.targetr.geofence

//#json-support
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val testGeoRangeSampleFormat: RootJsonFormat[TestGeoRangeSample] = jsonFormat7(TestGeoRangeSample)
  implicit val testGeoRangeFormat: RootJsonFormat[TestGeoRange] = jsonFormat6(TestGeoRange)
  implicit val testRangeSampleFormat: RootJsonFormat[TestRangeSample] = jsonFormat6(TestRangeSample)
  implicit val testRangeFormat: RootJsonFormat[TestRange] = jsonFormat5(TestRange)

  implicit val testGeoSlotSampleFormat: RootJsonFormat[TestGeoSlotSample] = jsonFormat5(TestGeoSlotSample)
  implicit val testGeoSlotFormat: RootJsonFormat[TestGeoSlot] = jsonFormat4(TestGeoSlot)
  implicit val testSlotSampleFormat: RootJsonFormat[TestSlotSample] = jsonFormat4(TestSlotSample)
  implicit val testSlotFormat: RootJsonFormat[TestSlot] = jsonFormat3(TestSlot)

  implicit val testGeoSampleFormat: RootJsonFormat[TestGeoSample] = jsonFormat4(TestGeoSample)
  implicit val testGeoFormat: RootJsonFormat[TestGeo] = jsonFormat3(TestGeo)
  implicit val testSampleFormat: RootJsonFormat[TestSample] = jsonFormat3(TestSample)
  implicit val testFormat: RootJsonFormat[Test] = jsonFormat2(Test)

  implicit val sizeJsonFormat: RootJsonFormat[Sample] = jsonFormat1(Sample)
  implicit val sampleJsonFormat: RootJsonFormat[SubSample] = jsonFormat1(SubSample)
  implicit val setSampleJsonFormat: RootJsonFormat[SetSample] = jsonFormat1(SetSample)
  implicit val setSubSampleJsonFormat: RootJsonFormat[SetSubSample] = jsonFormat2(SetSubSample)
  implicit val uploadJsonFormat: RootJsonFormat[Uploaded] = jsonFormat3(Uploaded)
  implicit val opensonFormat: RootJsonFormat[Opened] = jsonFormat3(Opened)
  implicit val pointsJsonFormat: RootJsonFormat[PointsFound] = jsonFormat4(PointsFound)
}
