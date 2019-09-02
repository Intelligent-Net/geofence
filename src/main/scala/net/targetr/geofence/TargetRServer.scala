package net.targetr.geofence

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object TargetRServer extends App with GeoRoutes {
  val host = if (args.length >= 1) args(0) else "localhost"
  val port = (if (args.length >= 2) args(1) else "8080").toInt
  val fields = if (args.length >= 3)
                 args(2).split("\\s*,\\s*") match { case Array(a, b, c) => (a.toInt, b.toInt, c.toInt) }
               else
                 (1,2,0)
  // set up ActorSystem and other dependencies here
  implicit val system: ActorSystem = ActorSystem("TargetRHttpServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val geoRegistryActor: ActorRef = system.actorOf(GeoRegistryActor.props, "geoRegistryActor")

  // from the GeoRoutes trait
  lazy val routes: Route = geoRoutes

  //val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)
  //val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "ML1", 8080)
  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, host, port)

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }

  Await.result(system.whenTerminated, Duration.Inf)
}
