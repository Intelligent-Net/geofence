package net.targetr.geofence

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Route, ExceptionHandler}
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.Future
import net.targetr.geofence.GeoRegistryActor._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.directives.OnSuccessMagnet

trait GeoRoutes extends JsonSupport {
  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[GeoRoutes])

  // other dependencies that GeoRoutes use
  def geoRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(10.minutes) // usually we'd obtain the timeout from the system's configuration

  private def runner[T](future: Future[PointsFound], itemId: String) = {
    onSuccess(future) { exec =>
      log.info(s"itemId: ${itemId} - found: ${exec.found}, total: ${exec.total}, success: ${exec.success}, message: ${exec.message}")
      complete(exec)
    }
  }

  // Catch all
  implicit def geofenceExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: Exception =>
        extractUri { uri => complete(s"$e calling $uri") }
    }

  lazy val geoRoutes: Route = withoutSizeLimit {
    pathPrefix("geofence") {
    concat(
      pathEndOrSingleSlash {
        withRequestTimeout(1.minutes) {
          concat(
            post {    // Order is important, must be before TestGeo
              entity(as[TestGeoRangeSample]) { geo =>
                runner((geoRegistryActor ? RunTestGeoRangeSample(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestGeoRange]) { geo =>
                runner((geoRegistryActor ? RunTestGeoRange(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestRangeSample]) { geo =>
                runner((geoRegistryActor ? RunTestRangeSample(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestRange]) { geo =>
                runner((geoRegistryActor ? RunTestRange(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestGeoSlotSample]) { geo =>
                runner((geoRegistryActor ? RunTestGeoSlotSample(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestGeoSlot]) { geo =>
                runner((geoRegistryActor ? RunTestGeoSlot(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestSlotSample]) { geo =>
                runner((geoRegistryActor ? RunTestSlotSample(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestSlot]) { geo =>
                runner((geoRegistryActor ? RunTestSlot(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestGeoSample]) { geo =>
                runner((geoRegistryActor ? RunTestGeoSample(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestGeo]) { geo =>
                runner((geoRegistryActor ? RunTestGeo(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[TestSample]) { geo =>
                runner((geoRegistryActor ? RunTestSample(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[Test]) { geo =>
                runner((geoRegistryActor ? RunTest(geo)).mapTo[PointsFound], geo.itemId)
              }
            },
            post {
              entity(as[SampleSize]) { sample =>
                val sSize: Future[SampleSize] = (geoRegistryActor ? SampleSize(sample.size)).mapTo[SampleSize]
                onSuccess(sSize) { size =>
                  complete((StatusCodes.Created, size))
                }
              }
            },
            post {
              entity(as[SetSubSampleSize]) { sample =>
                val sSize: Future[SetSubSampleSize] = (geoRegistryActor ? SetSubSampleSize(sample.areaId, sample.sample)).mapTo[SetSubSampleSize]
                onSuccess(sSize) { sample =>
                  complete((StatusCodes.Created, sample))
                }
              }
            }
            )
          }
      },
      path("size") {
        get { _.complete((geoRegistryActor ? GetSampleSize).mapTo[SampleSize]) }
      },
      path("sample" / Remaining) { id =>
        get { ctx =>
          /*
          val sSize: Future[SampleSize] = (geoRegistryActor ? GetSampleSize).mapTo[SampleSize]
          onSuccess(sSize) { size =>
            complete((StatusCodes.Created, size))
          }
          */
          //ctx.complete((StatusCodes.Created, GetSampleSize))
          val size = (geoRegistryActor ? GetSubSampleSize(id)).mapTo[SetSubSampleSize]
          ctx.complete(size)
        }
      },
      /*
      path(Segment) { para =>
        concat(
          get {
            para match {
              case "size" =>
                val sSize: Future[SampleSize] = (geoRegistryActor ? GetSampleSize).mapTo[SampleSize]
                onSuccess(sSize) { size =>
                  complete((StatusCodes.Created, size))
                }
              case "sample" =>
                val sSize: Future[SubSampleSize] = (geoRegistryActor ? GetSubSampleSize).mapTo[SubSampleSize]
                onSuccess(sSize) { sample =>
                  complete((StatusCodes.Created, sample))
                }
              case "data" =>
                withRequestTimeout(5.minutes) {
                  val data: Future[Data] = (geoRegistryActor ? GetData).mapTo[Data]
                  onSuccess(data) {
                    log.info("Data downloaded")

                    d => complete(d)
                  }
                }
              case _ =>
                complete((StatusCodes.Created, s"Unknown parameter $para"))
            }
          }
          )
      },
      */
      withRequestTimeout(5.minutes) {
        log.info("Starting upload")

        uploadedFile("file") {
          case (metadata, file) =>
            log.info(s"Starting processing ${metadata.fileName}")

            val geo: Future[Uploaded] = (geoRegistryActor ? LoadPolyFile(file.getAbsolutePath, metadata.fileName)).mapTo[Uploaded]
            onSuccess(geo) { exec =>
              log.info(s"Uploaded file ${metadata.fileName} to ${file.getAbsolutePath}")
              complete(exec)
            }
          }
      }
      )
    }
  }
}
