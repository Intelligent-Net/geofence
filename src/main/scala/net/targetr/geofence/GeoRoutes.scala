package net.targetr.geofence

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.MethodDirectives.{get, post}
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.pattern.ask
import akka.util.Timeout
import net.targetr.geofence.GeoRegistryActor._

import scala.concurrent.Future
import scala.concurrent.duration._

trait GeoRoutes extends JsonSupport {
  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[GeoRoutes])

  // other dependencies that GeoRoutes use
  def geoRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout: Timeout = Timeout(10.minutes) // usually we'd obtain the timeout from the system's configuration

  private def runner[T](future: Future[PointsFound], areaId: String, itemId: String) = {
    onSuccess(future) { exec =>
      log.info(s"areaId: $areaId, itemId: $itemId - found: ${exec.found}, total: ${exec.total}, success: ${exec.success}, message: ${exec.message}")
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
        withRequestTimeout(5.minutes) {
          concat(
            post {    // Order is important, must be before TestGeo
              entity(as[TestGeoRangeSample]) { geo =>
                runner((geoRegistryActor ? RunTestGeoRangeSample(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestGeoRange]) { geo =>
                runner((geoRegistryActor ? RunTestGeoRange(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestRangeSample]) { geo =>
                runner((geoRegistryActor ? RunTestRangeSample(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestRange]) { geo =>
                runner((geoRegistryActor ? RunTestRange(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestGeoSlotSample]) { geo =>
                runner((geoRegistryActor ? RunTestGeoSlotSample(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestGeoSlot]) { geo =>
                runner((geoRegistryActor ? RunTestGeoSlot(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestSlotSample]) { geo =>
                runner((geoRegistryActor ? RunTestSlotSample(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestSlot]) { geo =>
                runner((geoRegistryActor ? RunTestSlot(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestGeoSample]) { geo =>
                runner((geoRegistryActor ? RunTestGeoSample(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestGeo]) { geo =>
                runner((geoRegistryActor ? RunTestGeo(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[TestSample]) { geo =>
                runner((geoRegistryActor ? RunTestSample(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[Test]) { geo =>
                runner((geoRegistryActor ? RunTest(geo)).mapTo[PointsFound], geo.areaId, geo.itemId)
              }
            },
            post {
              entity(as[Sample]) { sample =>
                val sSize: Future[Sample] = (geoRegistryActor ? Sample(sample.size)).mapTo[Sample]
                onSuccess(sSize) { size =>
                  complete((StatusCodes.Created, size))
                }
              }
            },
            post {
              entity(as[SetSubSample]) { sample =>
                val sSize: Future[SetSubSample] = (geoRegistryActor ? SetSubSample(sample.areaId, sample.sample)).mapTo[SetSubSample]
                onSuccess(sSize) { sample =>
                  complete((StatusCodes.Created, sample))
                }
              }
            }
            )
          }
      },
      path("size") {
        log.info("Checking sample size")
        get { _.complete((geoRegistryActor ? GetSample).mapTo[Sample]) }
      },
      path("sample" / Remaining) { id =>
        log.info(s"Checking subsample size for $id")
        get { _.complete((geoRegistryActor ? SubSample(id)).mapTo[SetSubSample]) }
      },
      path("open" / Remaining) { id =>
        val openFn: Future[Opened] = (geoRegistryActor ? OpenPolyFile(id)).mapTo[Opened]
        onSuccess(openFn) { exec =>
          log.info(s"Opened file $id")
          complete(exec)
        }
      },
      withRequestTimeout(10.minutes) {
        log.info("Starting data upload")

        def dest(fileInfo: FileInfo): File =
          File.createTempFile(fileInfo.fileName, ".tmp")

        storeUploadedFile("file", dest) {
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
