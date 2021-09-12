import sbt.addCompilerPlugin

name := "Shahnameh"

version := "0.1"

scalaVersion := "2.13.6"

enablePlugins(ScalaJSPlugin)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

val catsEffectsVersion = "3.2.5"
val catsVersion = "2.6.1"
val http4sVersion = "0.23.1"
val circeVersion = "0.14.1"
val mouseVersion = "1.0.4"

scalacOptions in Global += "-Ymacro-annotations"

Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)

val backendDependencies = Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-free" % catsVersion,
  "org.typelevel" %% "cats-laws" % catsVersion,
  "org.typelevel" %% "alleycats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectsVersion,
  "org.typelevel" %% "cats-effect-laws" % catsEffectsVersion,

  "com.github.pureconfig" %% "pureconfig" % "0.16.0",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.16.0",

  "org.typelevel" %% "mouse" % mouseVersion,

  "com.chuusai" %% "shapeless" % "2.3.7",
  "org.typelevel" %% "log4cats-slf4j" % "2.1.1",
  "org.slf4j" % "slf4j-simple" % "1.7.32",

  "dev.optics" %% "monocle-core" % "3.0.0-RC2",
  "dev.optics" %% "monocle-macro" % "3.0.0-RC2",

  "com.beachape" %% "enumeratum" % "1.7.0",

  "co.fs2" %% "fs2-core" % "3.1.1",
  "com.github.fd4s" %% "fs2-kafka" % "2.2.0",

  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "org.apache.kafka" % "kafka-clients" % "2.8.0"
)
val backend = (project in file("backend"))
  .settings(
    scalaVersion := "2.13.6",
    libraryDependencies := backendDependencies,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
    Compile/mainClass := Some("ir.ashkan.shahnameh.demo.WebSocketServer"),
    Docker/packageName := "ghcr.io/ashkann/shahnameh-backend",
    executableScriptName := "websocket-server",
    dockerExposedPorts := Seq(8080),
  ).enablePlugins(JavaServerAppPackaging)

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
