import sttp.client._

object Client extends App {
  val sort: Option[String] = None
  val query = "http language:scala"

  // the `query` parameter is automatically url-encoded
  // `sort` is removed, as the value is not defined
  val request = basicRequest.post(uri"http://fraudwall.ignorelist.com:8080/geofence/file")
                            .multipartBody(multipartFile("file", new java.io.File("example.csv.gz")))

  implicit val backend = HttpURLConnectionBackend()
  //implicit val backend = AkkaHttpBackend()
  //implicit val backend = AsyncHttpClientFutureBackend()
  val response = request.send()

  //println(response.header("Content-Length"))

  // response.unsafeBody: by default read into a String
  println(response.body)
}
