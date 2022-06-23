val sharedSettings = Seq(
  scalaVersion := "2.13.8",
  scalacOptions += "-Ymacro-annotations",
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

val jvmDeps = {
  object Version {
    val catsEffects = "3.2.8"
    val cats = "2.6.1"
    val http4s = "0.23.6"
    val circe = "0.14.1"
    val mouse = "1.0.7"
    val sttp = "3.3.16"
    val doobie = "1.0.0-RC1"
  }

  Seq(
    "org.typelevel" %% "cats-core" % Version.cats,
    "org.typelevel" %% "cats-laws" % "2.6.1" % Test,
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.5" % Test,
    "org.typelevel" %% "cats-free" % Version.cats,
    "org.typelevel" %% "alleycats-core" % Version.cats,
    "org.typelevel" %% "mouse" % Version.mouse,
    "org.typelevel" %% "cats-effect" % Version.catsEffects,
    "org.typelevel" %% "cats-effect-laws" % "3.3-393-da7c7c7" % Test,

    "org.tpolecat" %% "doobie-core"      % Version.doobie,
    "org.tpolecat" %% "doobie-hikari"    % Version.doobie,
    "org.tpolecat" %% "doobie-postgres"  % Version.doobie,

    "io.circe" %% "circe-core" % Version.circe,
    "io.circe" %% "circe-generic" % Version.circe,
    "io.circe" %% "circe-parser" % Version.circe,

    "org.http4s" %% "http4s-blaze-server" % Version.http4s,
    "org.http4s" %% "http4s-circe" % Version.http4s,
    "org.http4s" %% "http4s-dsl" % Version.http4s,

    "com.github.pureconfig" %% "pureconfig" % "0.17.0",
    "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.0",

    "com.chuusai" %% "shapeless" % "2.3.7",
    "org.typelevel" %% "log4cats-slf4j" % "2.1.1",
    "org.slf4j" % "slf4j-simple" % "1.7.32",

    "dev.optics" %% "monocle-core" % "3.1.0",
    "dev.optics" %% "monocle-macro" % "3.1.0",

    "co.fs2" %% "fs2-core" % "3.2.2",
    "com.github.fd4s" %% "fs2-kafka" % "2.2.0",
    "org.apache.kafka" % "kafka-clients" % "2.8.0",

    "com.softwaremill.sttp.client3" %% "core" % Version.sttp,
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % Version.sttp,

    "com.github.jwt-scala" %% "jwt-core" % "9.0.2",

    "org.flywaydb" % "flyway-core" % "8.0.2"
  )
}

lazy val Shahnameh =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("."))
    .settings(sharedSettings)
    .jsSettings(libraryDependencies ++= Seq("org.scala-js" %%% "scalajs-dom" % "2.0.0"))
    .jvmSettings(
      libraryDependencies ++= jvmDeps,
      Compile / mainClass := Some("ir.ashkan.shahnameh.Server"),
      assembly / assemblyMergeStrategy := {
        case PathList("META-INF", _) => MergeStrategy.discard
        case other => (assembly / assemblyMergeStrategy).value(other)
      }
    )