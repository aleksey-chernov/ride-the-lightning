val tapirVersion = "1.13.33"

val reStart = taskKey[Unit]("(Re)starts the backend in the background; `~backend/reStart` restarts on changes")
val reStop = taskKey[Unit]("Stops the backend started with reStart")

ThisBuild / organization := "rtl"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.9.0"

lazy val backend = project
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "backend",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "com.softwaremill.ox" %% "core" % "1.0.9",
      "com.softwaremill.sttp.client4" %% "core" % "4.0.27",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.41.5",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
      "ch.qos.logback" % "logback-classic" % "1.6.5",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub4-server" % tapirVersion % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    buildInfoKeys := Seq[BuildInfoKey](BuildInfoKey("name" -> "ride-the-lightning"), version, scalaVersion),
    buildInfoPackage := "rtl",
    Compile / mainClass := Some("rtl.Main"),
    // separate JVM so Ctrl+C / reStop cleanly stop the app
    run / fork := true,
    // silence JDK 25 warnings from Scala runtime / Netty native libs
    run / javaOptions ++= Seq("--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED"),
    reStop := Def.uncached {
      val jobService = bgJobService.value
      val thisProject = Select(thisProjectRef.value)
      jobService.jobs.filter(_.spawningTask.scope.project == thisProject).foreach { job =>
        jobService.stop(job)
        scala.util.Try(jobService.waitFor(job))
      }
    },
    reStart := Def.uncached(Def.sequential(reStop, (Compile / bgRun).toTask("").map(_ => ())).value)
  )

lazy val root = (project in file("."))
  .aggregate(backend)
  .settings(name := "ride-the-lightning")
