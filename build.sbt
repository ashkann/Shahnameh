import sbt.addCompilerPlugin

name := "Shahnameh"

version := "0.1"

scalaVersion := "2.13.6"

enablePlugins(ScalaJSPlugin)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

val catsEffectsVersion = "3.1.0"
val catsVersion = "2.4.2"
val http4sVersion = "0.23.0-RC1"
val circeVersion = "0.14.1"
val mouseVersion = "1.0.4"

scalacOptions in Global += "-Ymacro-annotations"

Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)

val backend = (project in file("backend"))
  .settings(
    scalaVersion := "2.13.6",
    libraryDependencies := Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-free" % catsVersion,
      "org.typelevel" %% "cats-laws" % catsVersion,
      "org.typelevel" %% "alleycats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectsVersion,
      "org.typelevel" %% "cats-effect-laws" % catsEffectsVersion,

      "com.github.pureconfig" %% "pureconfig" % "0.16.0",

      "org.typelevel" %% "mouse" % mouseVersion,

      "com.chuusai" %% "shapeless" % "2.3.3",
      "org.typelevel" %% "log4cats-slf4j" % "2.0.1",
      "org.slf4j" % "slf4j-simple" % "1.7.30",

      "dev.optics" %% "monocle-core" % "3.0.0-RC2",
      "dev.optics" %% "monocle-macro" % "3.0.0-RC2",

      "com.beachape" %% "enumeratum" % "1.6.1",

      "co.fs2" %% "fs2-core" % "3.0.0",

      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "org.apache.kafka" % "kafka-clients" % "2.8.0"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
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
