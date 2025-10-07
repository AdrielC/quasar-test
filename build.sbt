import Versions.{zioVersion => zioV, _ }
import scalapb.compiler.Version
import scalapb.GeneratorOption._

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / organization := "io.quasar"

ThisBuild / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

ThisBuild / PB.protocVersion := "3.21.12"

inThisBuild(
  List(
    name := "Quasar",
    zioVersion := zioV,
    // crossScalaVersions -= scala211.value,
    developers := List(
      Developer(
        "acasellas",
        "Adriel Casellas",
        "acasellas@tybera.com",
        url("http://tybera.com")
      )
    ),
    ciEnabledBranches := Seq("main"),
    ciTargetScalaVersions :=
      Map(
        (`quasar-grpc` / thisProject).value.id -> (`quasar-grpc` / crossScalaVersions).value,
      ),
    
    // Performance and memory optimizations
    ThisBuild / turbo := true,
    ThisBuild / usePipelining := true,
    Global / concurrentRestrictions := Seq(
      Tags.limitAll(4), // Limit concurrent tasks to prevent memory exhaustion
      Tags.limit(Tags.Compile, 2) // Limit concurrent compilation
    )
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(
    `quasar-grpc`
  )
  .settings(
    name := "quasar-root",
    publish / skip := true
  )

lazy val `quasar-grpc` = project
  .in(file("quasar-grpc"))
  .enablePlugins(BuildInfoPlugin, DynVerPlugin)
  .settings(
    name := "quasar-grpc",
    description := "Quasar gRPC BlobStore Service",
    scalaVersion := scala3.value,
    buildInfoPackage := "io.quasar.grpc.buildinfo",
    buildInfoKeys := Seq[BuildInfoKey](
      name, 
      version, 
      scalaVersion,
    ),

    // ScalaPB configuration for protobuf compilation
    Compile / PB.targets := Seq(
      scalapb.gen(
        flatPackage = false,
        javaConversions = false,
        grpc = true,
        singleLineToProtoString = true,
        asciiFormatToString = true,
        lenses = true
      ) -> (Compile / sourceManaged).value / "scalapb",
      scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value / "scalapb"
    ),

    libraryDependencies ++= Seq(
      // ZIO ecosystem
      "dev.zio" %% "zio"                    % zioVersion.value,
      "dev.zio" %% "zio-json"               % zioJsonVersion,
      "dev.zio" %% "zio-logging"            % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j"      % zioLoggingVersion,
      "dev.zio" %% "zio-schema-derivation"  % zioSchemaVersion,
      "dev.zio" %% "zio-schema-protobuf"    % zioSchemaVersion,
      "dev.zio" %% "zio-schema-json"        % zioSchemaVersion,
      "dev.zio" %% "zio-config"             % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia"    % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe"    % zioConfigVersion,
      "dev.zio" %% "zio-prelude"            % "1.0.0-RC16",
      "dev.zio" %% "zio-mock"               % "1.0.0-RC9" % Test,

      // gRPC / Scalapb
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % Version.scalapbVersion,
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0" % "protobuf",
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0",

      // scodec
      "org.scodec" %% "scodec-bits" % "1.2.4",


      "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-core" % "0.6.3",

      "io.grpc" % "grpc-netty" % grpcVersion,

      // Logging backend
      "ch.qos.logback" % "logback-classic" % "1.4.4",

      // Testing
      "dev.zio" %% "zio-test"         % zioVersion.value % Test,
      "dev.zio" %% "zio-test-junit"   % zioVersion.value % Test,
      "dev.zio" %% "zio-test-sbt"     % zioVersion.value % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion.value % Test,
    ),

    // Compiler optimizations
    javacOptions ++= Seq("-source", "21", "-target", "21"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-Wsafe-init",
      "-Xmx-strict", // Strict memory management for Scala 3
    ),
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
    
    // Reduce memory usage during compilation
    Compile / compile / javacOptions ++= Seq(
      "-J-Xmx2G",
      "-J-XX:MaxMetaspaceSize=512M"
    ),
    
    // Optimize test execution
    Test / fork := true,
    Test / javaOptions ++= Seq(
      "-Xmx2G",
      "-XX:MaxMetaspaceSize=512M"
    )
  )

