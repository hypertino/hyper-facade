crossScalaVersions := Seq(/*"2.12.1", */"2.11.8")

scalaVersion in Global := "2.11.8"

organization := "com.hypertino"

name := "hyper-facade"

version := "0.2-SNAPSHOT"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

buildInfoPackage := "com.hypertino.facade"

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)

lazy val root = (project in file(".")). enablePlugins(BuildInfoPlugin) //, Raml2Hyperbus)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

libraryDependencies ++= Seq(
  "ch.qos.logback"       % "logback-classic"              % "1.1.8",
  "ch.qos.logback"       % "logback-core"                 % "1.1.8",
  "com.typesafe.akka"    %% "akka-actor"                  % "2.4.18",
  "com.typesafe.akka"    %% "akka-cluster"                % "2.4.18",
  "com.wandoulabs.akka"  %% "spray-websocket"             % "0.1.4",
  "com.hypertino"               %% "binders"                % "1.0-SNAPSHOT",
  "com.hypertino"               %% "expression-parser"           % "0.1-SNAPSHOT",
//  "com.hypertino"               %% "auth-service-model"          % "0.1.7",
  "com.hypertino"               %% "hyperbus"                    % "0.2-SNAPSHOT",
  "com.hypertino"               %% "hyperbus-model"              % "0.2-SNAPSHOT",
  "com.hypertino"               %% "hyperbus-transport"          % "0.2-SNAPSHOT",
  "com.hypertino"               %% "hyperbus-t-kafka"            % "0.2-SNAPSHOT",
  "com.hypertino"               %% "hyperbus-t-zeromq"           % "0.2-SNAPSHOT",
  "com.hypertino"               % "raml-parser-2"                % "1.0.5-SNAPSHOT",
  "com.hypertino"               %% "service-control"             % "0.3-SNAPSHOT",
  "com.hypertino"               %% "service-config"              % "0.2-SNAPSHOT",
  "com.hypertino"               %% "service-metrics"             % "0.3-SNAPSHOT",
  "jline"                % "jline"                        % "2.14.4",
  "io.spray"             %% "spray-can"                   % "1.3.1",
  "io.spray"             %% "spray-routing-shapeless2"    % "1.3.3",
  "io.spray"             %% "spray-client"                % "1.3.1"     % "test",
  "org.scaldi"           %% "scaldi"                      % "0.5.8",
  "org.scalamock"        %% "scalamock-scalatest-support" % "3.5.0" % "test",
//  "com.hypertino"               %% "simple-auth-service"         % "0.1.13"    % "test",
  "org.pegdown"          % "pegdown"                      % "1.4.2"     % "test"
)

fork in Test := true

parallelExecution in Test := false
