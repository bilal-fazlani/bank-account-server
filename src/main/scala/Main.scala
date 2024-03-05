import zio.*
import zio.http.Server
import zio.logging.ConsoleLoggerConfig
import zio.logging.LogFormat
import zio.logging.LogFilter.LogLevelByNameConfig

object Main extends ZIOAppDefault:

  override val bootstrap =
    Runtime.removeDefaultLoggers ++ zio.logging.consoleLogger(
      ConsoleLoggerConfig(LogFormat.colored, LogLevelByNameConfig.default)
    )

  val program = for {
    app <- ZIO.serviceWith[AccountRoutes](_.routes.toHttpApp)
    _ <- Server.serve(app) zipPar ZIO.logInfo(
      "Server started on port 9000. documentation at http://localhost:9000/docs/openapi"
    )
  } yield ()

  def run = program
    .provideSome[Scope](
      Server.defaultWith(_.port(9000)),
      AccountRoutes.live,
      AccountService.live,
      ZLayer(Ref.Synchronized.make(Map.empty[String, Int]))
    )
