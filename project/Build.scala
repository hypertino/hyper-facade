import sbt.Keys._
import sbt._

object Build extends sbt.Build {
  lazy val paradiseVersionRoot = "2.1.0-M5"

  val projectMajorVersion = settingKey[String]("Defines the major version number")
  val projectBuildNumber = settingKey[String]("Defines the build number")

  override lazy val settings: Seq[Setting[_]] =
    super.settings ++ Seq(
        organization := "eu.inn",
        scalaVersion := "2.11.7",
        projectMajorVersion := "0.1",
        projectBuildNumber := "SNAPSHOT",
        version := projectMajorVersion.value + "." + projectBuildNumber.value,

        scalacOptions ++= Seq(
          "-language:postfixOps",
          "-feature",
          "-deprecation",
          "-unchecked",
          "-optimise",
          "-target:jvm-1.8",
          "-encoding", "UTF-8"
        ),

        javacOptions ++= Seq(
          "-source", "1.8",
          "-target", "1.8",
          "-encoding", "UTF-8",
          "-Xlint:unchecked",
          "-Xlint:deprecation"
        ),

        resolvers ++= Seq(
          "Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local",
          "Innova ext repo" at "http://repproxy.srv.inn.ru/artifactory/ext-release-local",
          Resolver.sonatypeRepo("public")
        ),

        libraryDependencies ++= Seq(
          "io.dropwizard.metrics"          % "metrics-core"                 % "3.1.2",
          "io.dropwizard.metrics"          % "metrics-graphite"             % "3.1.2",
          "ch.qos.logback"                 % "logback-classic"              % "1.1.2",
          "ch.qos.logback"                 % "logback-core"                 % "1.1.2",
          "com.fasterxml.jackson.core"     % "jackson-annotations"          % "2.4.4",
          "com.fasterxml.jackson.core"     % "jackson-core"                 % "2.4.4",
          "com.fasterxml.jackson.core"     % "jackson-databind"             % "2.4.4",
          "com.fasterxml.jackson.datatype" % "jackson-datatype-joda"        % "2.4.3",
          "com.fasterxml.jackson.module"   %% "jackson-module-scala"        % "2.4.3",
          "com.typesafe.akka"              %% "akka-actor"                  % "2.4.1",
          "com.typesafe.akka"              %% "akka-cluster"                % "2.4.1",
          //"com.typesafe.akka"              %% "akka-testkit"                % "2.4.1"     % "test",
          "com.wandoulabs.akka"            %% "spray-websocket"             % "0.1.4",
          "eu.inn"                         %% "binders-core"                % "0.11.77",
          "eu.inn"                         %% "hyperbus"                    % "0.1.64",
          "eu.inn"                         %% "hyperbus-model"              % "0.1.64",
          "eu.inn"                         %% "hyperbus-transport"          % "0.1.64",
          "eu.inn"                         %% "hyperbus-t-kafka"            % "0.1.64",
          "eu.inn"                         %% "hyperbus-t-distributed-akka" % "0.1.64",
          "eu.inn"                         % "java-raml1-parser"            % "0.0.30",
          "eu.inn"                         % "javascript-module-holders"    % "0.0.30",
          "eu.inn"                         %% "service-control"             % "0.1.16",
          "eu.inn"                         %% "service-config"              % "0.1.3",
          "jline"                          % "jline"                        % "2.12.1",
          "io.spray"                       %% "spray-can"                   % "1.3.3",
          "io.spray"                       %% "spray-routing-shapeless2"    % "1.3.3",
          "io.spray"                       %% "spray-client"                % "1.3.3"     % "test",
          "org.scaldi"                     %% "scaldi"                      % "0.5.7",
          "org.scalatest"                  %% "scalatest"                   % "2.2.1"     % "test",
          "org.pegdown"                    % "pegdown"                      % "1.4.2"     % "test"
        ),

        fork in Test := true,
        publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local"),
        credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials"),

        addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersionRoot cross CrossVersion.full)
      )
}