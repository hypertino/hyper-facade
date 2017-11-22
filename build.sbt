crossScalaVersions := Seq(/*"2.12.3" -- SPRAY ISN'T SUPPORTE,*/ "2.11.11")

scalaVersion := crossScalaVersions.value.head

organization := "com.hypertino"

name := "hyperfacade"

version := "0.3.2-SNAPSHOT"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

buildInfoPackage := "com.hypertino.facade"

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

lazy val root = (project in file(".")).enablePlugins(BuildInfoPlugin) //, Raml2Hyperbus)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "com.hypertino" %% "binders" % "1.2.0",
  "com.hypertino" %% "expression-parser" % "0.2.0",
  //  "com.hypertino"        %% "auth-service-model"          % "0.1.7",
  "com.hypertino" %% "hyperbus" % "0.3-SNAPSHOT",
  "com.hypertino" % "raml-parser-2" % "1.0.16",
  "com.hypertino" %% "hyperbus-utils" % "0.1-SNAPSHOT",
  "com.hypertino" %% "service-control" % "0.3.0",
  "com.hypertino" %% "service-config" % "0.2.0",
  "com.hypertino" %% "service-metrics" % "0.3.0",
  "org.scaldi" %% "scaldi" % "0.5.8",
  "io.spray" %% "spray-can" % "1.3.1",
  "io.spray" %% "spray-routing-shapeless2" % "1.3.3",
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
  "com.typesafe.akka" %% "akka-actor" % "2.4.20",
  "com.hypertino" %% "hyperbus-t-inproc" % "0.3-SNAPSHOT" % "test",
  "org.asynchttpclient" % "async-http-client" % "2.0.37" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test",
  //  "com.hypertino"               %% "simple-auth-service"         % "0.1.13"    % "test",
  "org.pegdown" % "pegdown" % "1.6.0" % "test"
)

fork in Test := true

parallelExecution in Test := false

ramlHyperbusSources := Seq(
  ramlSource(
    path = "api/auth-service-api/auth.raml",
    packageName = "com.hypertino.facade.apiref.auth",
    isResource = false
  ),
  ramlSource(
    path = "api/user-service-api/user.raml",
    packageName = "com.hypertino.facade.apiref.user",
    isResource = false
  ),
  ramlSource(
    path = "api/idempotency-service-api/idempotency.raml",
    packageName = "com.hypertino.facade.apiref.idempotency",
    isResource = false
  )
)
