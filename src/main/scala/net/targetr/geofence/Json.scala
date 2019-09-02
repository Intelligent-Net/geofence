package net.targetr.geofence

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
//import scala.util.parsing.json.JSON
import scala.util.{Try, Success, Failure}
import scala.io.Source
//import scala.collection.mutable._

object Json {
  private val mapper = new ObjectMapper() with ScalaObjectMapper

  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def readFile(fn: String): String = {
    using(Source.fromFile(fn)) { _.mkString }
  }

  /*
  def getPolygons(jsonStr: String): Set[(String, Int, Array[(Double,Double)])] = {
    val stacks = toMap(jsonStr)("stacks").asInstanceOf[List[Map[String,Any]]]
    val polys = "\"([^\"]*)\"".r

    val versions =
      for (stack <- stacks)
        yield(stack.asInstanceOf[Map[String,Any]]("items").asInstanceOf[List[Map[String,Any]]].map(_("data").asInstanceOf[Map[String,Any]]).filter(_.contains("condition")).map(data => (data("itemId").toString, data("version").toString.toInt)))
    // Get highest versions only
    val items = scala.collection.mutable.Set[String]()
    val pairs = versions
                  .flatMap(v => v)
                  .sorted(Ordering.Tuple2[String,Int].reverse)
                  .filter(i => items.add(i._1))
                  .toSet
    //println(pairs)

    var parts = scala.collection.mutable.Set[(String, Int, Array[(Double,Double)])]()

    for (stack <- stacks) {
      val item = stack.asInstanceOf[Map[String,Any]]("items").asInstanceOf[List[Map[String,Any]]]
      val datas = item.map(_("data").asInstanceOf[Map[String,Any]])
      for (data <- datas if data.contains("condition") && pairs((data("itemId").toString, data("version").toString.toInt))) {
        //println(s"${data("itemId")} : ${data("version")}")
        val cond = data("condition").toString.trim

        if (cond.contains("gpsInside")) {
          val coords = polys.findAllIn(cond).map(_.replaceAll("\"","").split(",").map(_.toDouble)).toArray

          parts.add((data("itemId").toString, data("version").toString.toInt, Poly.points(coords)))
        }
      }
    }

    parts.toSet
  }
  */

  def getCircle(cond: String): (Double, Double, Double, String) = {
    val circle = "\"([^\"]*)\"".r
    val parts = circle.findAllIn(cond).toList(0).replaceAll("\"", "").split("\\s*,\\s*")

    (parts(0).toDouble, parts(1).toDouble, parts(2).toDouble, parts(3))
  }

  def getCoords(cond: String): Seq[(Double, Double)] = {
    val polys = "\"([^\"]*)\"".r
    val arr = polys.findAllIn(cond).flatMap(_.replaceAll("\"","").split("\\s*,\\s*").map(_.toDouble)).toArray

    for (j <- 0 to arr.length if (j % 2 == 1))
      yield((arr(j-1),arr(j)))
  }

  def toJson(value: Any): String = {
    mapper.writeValueAsString(value)
  }

  def toMap(json: String): Map[String,Any] = {
    mapper.readValue(json, classOf[Map[String,Any]])
  }

  def using[A <: { def close(): Unit }, B](param: A)(f: A => B): B =
    try {
      f(param)
    }
    finally {
      param.close()
    }
}
