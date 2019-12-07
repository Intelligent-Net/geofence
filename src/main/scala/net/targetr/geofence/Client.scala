package net.targetr.geofence.Client

import sttp.client._
import sttp.client.akkahttp._
import sttp.client.json4s._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Query(areaId: String, itemId: String, condition: String = "", slot: String = "")
case class SetSample(size: Double)
case class SetSubSample(areaId: String, sample: Double)

object Client {
  def main(args: Array[String]) = {
    val host = if (args.length >= 1) args(0) else "localhost"
    val port = (if (args.length >= 2) args(1) else "8080").toInt
    val call = if (args.length >= 3) args(2) else "file"
    val paras = if (args.length >= 4) args.drop(3) else args
    implicit val serialization = org.json4s.native.Serialization
    val request = 
      call match {
        case "file"|"upload" => basicRequest.post(uri"http://$host:$port/geofence/$call")
          .multipartBody(multipartFile("file", new java.io.File(paras(0))))
        case "size" => basicRequest.get(uri"http://$host:$port/geofence/size")
        case "sample" => basicRequest.get(uri"http://$host:$port/geofence/sample/${paras(0)}")
        case "setsize" => basicRequest.post(uri"http://$host:$port/geofence")
          .body(SetSample(paras(0).toDouble))
        case "setsample" => basicRequest.post(uri"http://$host:$port/geofence")
          .body(SetSubSample(paras(0), paras(1).toDouble))
        case "open" => basicRequest.get(uri"http://$host:$port/geofence/open/${paras(0)}")
          /*
        case p if p.startsWith("size/") => basicRequest.get(uri"http://$host:$port/geofence/size/${p.substring(5)}")
        case p if p.startsWith("sample/") => basicRequest.get(uri"http://$host:$port/geofence/sample/${p.substring(7)}")
          */
        case "query" => basicRequest.post(uri"http://$host:$port/geofence")
          .body(paras.length match {
            case 2 => Query(paras(0), paras(1))
            case 3 => if (paras(2) contains "gps")
                        Query(paras(0), paras(1), condition=paras(2))
                      else
                        Query(paras(0), paras(1), slot=paras(2))
            case 4 => if (paras(2) contains "gps")
                        Query(paras(0), paras(1), condition=paras(2), slot=paras(3))
                      else
                        Query(paras(0), paras(1), slot=paras(2), condition=paras(3))
          })
          //.response(asJson[HttpBinResponse])
      }

    implicit val backend = AkkaHttpBackend()
    val response = request.send() // Must be a future

    for { r <- response } {
      println(s"Got response code: ${r.code}")
      println(r.body)
      backend.close()
    }
  }
}

/*
object UploadCsv extends App {
  val sort: Option[String] = None
  val query = "http language:scala"

  // the `query` parameter is automatically url-encoded
  // `sort` is removed, as the value is not defined
  //val request = basicRequest.post(uri"http://fraudwall.ignorelist.com:8080/geofence/file")
  val request = basicRequest.post(uri"http://localhost:8080/geofence/file")
                            .multipartBody(multipartFile("file", new java.io.File("example.csv.gz")))

  implicit val backend = HttpURLConnectionBackend()
  val response = request.send()

  println(response.body)
}
*/
