import sbt.addCompilerPlugin

name := "Shahnameh"

version := "0.1"

scalaVersion := "2.13.6"

enablePlugins(ScalaJSPlugin)

val catsEffectsVersion = "3.2.8"
val catsVersion = "2.6.1"
val http4sVersion = "0.23.4"
val circeVersion = "0.14.1"
val mouseVersion = "1.0.4"
val sttpVersion = "3.3.15"

scalacOptions in Global += "-Ymacro-annotations"

val backendDependencies = Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-free" % catsVersion,
  "org.typelevel" %% "cats-laws" % catsVersion,
  "org.typelevel" %% "alleycats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectsVersion,
  "org.typelevel" %% "cats-effect-laws" % catsEffectsVersion,

  "com.github.pureconfig" %% "pureconfig" % "0.17.0",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.0",

  "org.typelevel" %% "mouse" % mouseVersion,

  "com.chuusai" %% "shapeless" % "2.3.7",
  "org.typelevel" %% "log4cats-slf4j" % "2.1.1",
  "org.slf4j" % "slf4j-simple" % "1.7.32",

  "dev.optics" %% "monocle-core" % "3.1.0",
  "dev.optics" %% "monocle-macro" % "3.1.0",

  "co.fs2" %% "fs2-core" % "3.1.4",
  "com.github.fd4s" %% "fs2-kafka" % "2.2.0",
  "org.apache.kafka" % "kafka-clients" % "2.8.0",

  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,

  "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % sttpVersion,

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "com.github.jwt-scala" %% "jwt-core" % "9.0.2",
)

val backend = (project in file("backend"))
  .settings(
    scalaVersion := "2.13.6",
    libraryDependencies := backendDependencies,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    Compile / mainClass := Some("ir.ashkan.shahnameh.demo.WebSocketServer"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _) => MergeStrategy.discard
      case other => (assembly / assemblyMergeStrategy).value(other)
    }
  )

val WebClient = (project in file("WebClient"))
  .settings(
    scalaVersion := "2.13.6",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "1.1.0"
    ),
//    Compile / npmDependencies ++= Seq(
//      "jquery" -> "3.6.0"
//    )
  )
  .dependsOn(backend)
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)

val Shahname = (project in file("."))
  .aggregate(backend, WebClient)
