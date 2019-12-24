scalaVersion := "2.13.1"
lazy val akkaHttpVersion = "10.1.10"
lazy val akkaVersion    = "2.6.0"

name := "geofence"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
  "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC5",
  "com.softwaremill.sttp.client" %% "akka-http-backend" % "2.0.0-RC5",
  "com.softwaremill.sttp.client" %% "json4s" % "2.0.0-RC5",
  "org.json4s" %% "json4s-native" % "3.6.7",
  //"org.apache.commons" % "commons-jexl3" % "3.1",
  //"com.lihaoyi" %% "fastparse" % "2.1.3",
  "org.plotly-scala" %% "plotly-core" % "0.7.2",
  "org.plotly-scala" %% "plotly-render" % "0.7.2",

  "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
  "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
  "org.scalatest"     %% "scalatest"            % "3.0.8"         % Test
)
