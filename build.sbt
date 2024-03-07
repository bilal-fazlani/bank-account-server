val ZIO = "dev.zio"
val KIT = "io.github.kitlangton"
val scala3Version = "3.4.0"
val zioVersion = "2.0.21"

lazy val root = project
  .in(file("."))
  .settings(
    name := "bank-account-server",
    version := "0.2.0-SNAPSHOT",
    scalaVersion := scala3Version,
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    organization := "com.bilal-fazlani",
    libraryDependencies ++= Seq(
      ZIO %% "zio" % zioVersion,
      ZIO %% "zio-http" % "3.0.0-RC4+81-57502a13-SNAPSHOT",
      KIT %% "neotype" % "0.2.1",
      KIT %% "neotype-zio-schema" % "0.2.1",
      ZIO %% "zio-logging" % "2.2.0",
      ZIO %% "zio-test" % zioVersion % Test,
      ZIO %% "zio-test-sbt" % zioVersion % Test
    )
  )
