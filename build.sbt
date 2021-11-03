import sbt.addCompilerPlugin

name := "Shahnameh"

version := "0.1"

scalaVersion := "2.13.6"

enablePlugins(ScalaJSPlugin)

scalacOptions in Global += "-Ymacro-annotations"

val backendDependencies = {
  val catsEffectsVersion = "3.2.8"
  val catsVersion = "2.6.1"
  val http4sVersion = "0.23.6"
  val circeVersion = "0.14.1"
  val mouseVersion = "1.0.6"
  val sttpVersion = "3.3.15"
  val doobieVersion = "1.0.0-RC1"

  Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    "org.typelevel" %% "alleycats-core" % catsVersion,

    "org.typelevel" %% "mouse" % mouseVersion,

    "org.typelevel" %% "cats-effect" % catsEffectsVersion,

    "org.tpolecat" %% "doobie-core"      % doobieVersion,
    "org.tpolecat" %% "doobie-hikari"    % doobieVersion,
    "org.tpolecat" %% "doobie-postgres"  % doobieVersion,

    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,

    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,

    "com.github.pureconfig" %% "pureconfig" % "0.17.0",
    "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.0",

    "com.chuusai" %% "shapeless" % "2.3.7",
    "org.typelevel" %% "log4cats-slf4j" % "2.1.1",
    "org.slf4j" % "slf4j-simple" % "1.7.32",

    "dev.optics" %% "monocle-core" % "3.1.0",
    "dev.optics" %% "monocle-macro" % "3.1.0",

    "co.fs2" %% "fs2-core" % "3.1.6",
    "com.github.fd4s" %% "fs2-kafka" % "2.2.0",
    "org.apache.kafka" % "kafka-clients" % "2.8.0",

    "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % sttpVersion,

    "com.github.jwt-scala" %% "jwt-core" % "9.0.2",

    "org.flywaydb" % "flyway-core" % "8.0.1",
  )
}

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
        "org.scala-js" %%% "scalajs-dom" % "2.0.0"
    ),
//    Compile / npmDependencies ++= Seq(
//      "jquery" -> "3.6.0"
//    )
  )
  .dependsOn(backend)
  .enablePlugins(ScalaJSPlugin)

val Shahname = (project in file("."))
  .aggregate(backend, WebClient)