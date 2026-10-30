import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

import scala.sys.process.Process

val tapirVersion = "1.13.33"
// JDK 25 warnings from the Scala runtime / Netty native libs
val jvmFlags = Seq("--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED")

val reStart = taskKey[Unit]("(Re)starts the backend in the background; `~backend/reStart` restarts on changes")
val reStop = taskKey[Unit]("Stops the backend started with reStart")
val buildUi = taskKey[Seq[File]]("Builds the UI with pnpm and copies ui/dist to the webapp resources")

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
      "com.softwaremill.sttp.tapir" %% "tapir-files" % tapirVersion,
      "com.softwaremill.ox" %% "core" % "1.0.9",
      "com.softwaremill.sttp.client4" %% "core" % "4.0.27",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.41.5",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
      "ch.qos.logback" % "logback-classic" % "1.6.5",
      "com.augustnagro" %% "magnum" % "1.3.1",
      "org.xerial" % "sqlite-jdbc" % "3.53.4.0",
      "org.flywaydb" % "flyway-core" % "13.10.0", // SQLite support is built into core
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub4-server" % tapirVersion % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    buildInfoKeys := Seq[BuildInfoKey](BuildInfoKey("name" -> "ride-the-lightning"), version, scalaVersion),
    buildInfoPackage := "rtl",
    Compile / mainClass := Some("rtl.Main"),
    // separate JVM so Ctrl+C / reStop cleanly stop the app
    run / fork := true,
    run / javaOptions ++= jvmFlags,
    // a cached compile restores target/.../classes from sbt 2's cache, old resources included, and IntelliJ runs
    // rtl.Main from there: rewrite the current resources after every compile (copyResources' own cache misses this)
    Compile / compile := Def.uncached(Def.taskDyn {
      val analysis = (Compile / compile).value
      Def.task {
        val classes = (Compile / classDirectory).value
        val dirs = (Compile / resourceDirectories).value
        for ((file, path) <- (Compile / resources).value.filter(_.isFile).pair(Path.relativeTo(dirs))) {
          val target = classes / path
          java.nio.file.Files.deleteIfExists(target.toPath) // may be a symlink into the read-only cache
          IO.copyFile(file, target)
        }
        analysis
      }
    }.value),
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

// Packages backend + built UI into one image; not aggregated, so `compile`/`testFull` don't run pnpm.
lazy val docker = project
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .dependsOn(backend)
  .settings(
    buildUi := Def.uncached {
      val uiDir = (ThisBuild / baseDirectory).value / "ui"
      val log = streams.value.log
      Seq("pnpm install --frozen-lockfile", "pnpm build").foreach { cmd =>
        log.info(s"Running $cmd in $uiDir")
        if (Process(cmd, uiDir).! != 0) sys.error(s"$cmd failed")
      }
      val target = (Compile / resourceManaged).value / "webapp"
      IO.delete(target)
      IO.copyDirectory(uiDir / "dist", target)
      (target ** "*").filter(_.isFile).get()
    },
    // no sources here: the jar holds just the UI, served by the backend from `webapp/` on the classpath
    Compile / resourceGenerators += buildUi.taskValue,
    Compile / mainClass := Some("rtl.Main"),
    executableScriptName := "ride-the-lightning",
    makeBatScripts := Seq.empty,
    // heap = 60% of the container's memory (the JVM default is 25%)
    Universal / javaOptions ++= (jvmFlags :+ "-XX:MaxRAMPercentage=60").map("-J" + _),
    dockerBaseImage := "eclipse-temurin:25-jre",
    dockerExposedPorts := Seq(8080),
    // listen on all interfaces inside the container; shapes go to a volume (see docker-compose.yml)
    dockerEnvVars := Map("HTTP_HOST" -> "0.0.0.0", "GEO_CACHE_DIR" -> "/opt/docker/data/geo"),
    // the app user (1001) must own the shapes dir: a new named volume copies the image dir's owner
    dockerCommands := {
      val cmds = dockerCommands.value
      val switchToAppUser = cmds.lastIndexWhere { case Cmd("USER", _*) => true; case _ => false }
      cmds.patch(
        switchToAppUser,
        Seq(ExecCmd("RUN", "install", "-d", "-o", "1001", "-g", "0", "/opt/docker/data/geo")),
        0
      )
    },
    Docker / packageName := "ride-the-lightning",
    dockerUpdateLatest := true
  )

lazy val root = (project in file("."))
  .aggregate(backend)
  .settings(name := "ride-the-lightning")
