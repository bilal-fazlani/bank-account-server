val ZIO = "dev.zio"
val scala3Version = "3.4.0"
val zioVersion = "2.0.21"

lazy val root = project
  .in(file("."))
  .settings(
    name := "bank-account-server",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    libraryDependencies ++= Seq(
      ZIO %% "zio" % zioVersion,
      ZIO %% "zio-http" % "3.0.0-RC4+81-57502a13-SNAPSHOT",
      ZIO %% "zio-logging" % "2.2.0",
      ZIO %% "zio-test" % zioVersion % Test,
      ZIO %% "zio-test-sbt" % zioVersion % Test
    )
  )
