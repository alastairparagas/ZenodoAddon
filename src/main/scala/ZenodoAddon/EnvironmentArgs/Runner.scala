package ZenodoAddon.EnvironmentArgs

import scala.util.Try


/**
  * Wrapper for all the IO action we want to execute (reading
  * from environment variables or through passed program
  * arguments) where we can use such arguments to startup
  * the service
  */
class Runner
{

  private def tryCliArgs(args: Array[String]) = Try({
    val cliArgs = new CLIArgs(args)
    cliArgs.verify()

    EnvironmentArgsRecord(
      graphEngineDsn = cliArgs.graphEngineDsn.getOrElse(""),
      graphSettingsFile = cliArgs.graphSettingsFile.getOrElse(""),
      port = Integer.parseInt(cliArgs.port.getOrElse("80")),
      redisDsn = cliArgs.redisDsn.getOrElse(""),
      postgresDsn = cliArgs.postgresDsn.getOrElse(""),
      postgresUser = cliArgs.postgresUser.getOrElse(""),
      postgresPassword = cliArgs.postgresPassword.getOrElse(""),
      postgresDb = cliArgs.postgresDb.getOrElse("")
    )
  })

  private def tryEnvironmentVars() = Try({
    val graphEngineDsn = System.getenv("GRAPH_ENGINE_DSN")
    val graphSettingsFile = System.getenv("GRAPH_SETTINGS_FILE")
    val port = System.getenv("PORT")
    val redisDsn = System.getenv("REDIS_DSN")
    val postgresDsn = System.getenv("POSTGRES_DSN")
    val postgresUser = System.getenv("POSTGRES_USER")
    val postgresPassword = System.getenv("POSTGRES_PASSWORD")
    val postgresDb = System.getenv("POSTGRES_DB")

    val environmentVarsIsOk = List(
      Validator.validateGraphEngineDsn(graphEngineDsn),
      Validator.validateGraphSettingsFile(graphSettingsFile),
      Validator.validatePort(port),
      Validator.validateRedisDsn(redisDsn),
      Validator.validatePostgresDsn(postgresDsn),
      Validator.validatePostgresUser(postgresUser),
      Validator.validatePostgresPassword(postgresPassword),
      Validator.validatePostgresDb(postgresDb)
    )
      .fold(true)({
        case (true, true) => true
        case (_, _) => false
      })

    environmentVarsIsOk match {
      case true => EnvironmentArgsRecord(
        graphEngineDsn = graphEngineDsn,
        graphSettingsFile = graphSettingsFile,
        port = Integer.parseInt(port),
        redisDsn = redisDsn,
        postgresDsn = postgresDsn,
        postgresUser = postgresUser,
        postgresPassword = postgresPassword,
        postgresDb = postgresDb
      )
      case false => throw new RuntimeException(
        "Required program config (obtained from input parameters" +
          " or environment vars) are incorrectly provided or not provided" +
          " at all."
      )
    }
  })

  def run(args: Array[String]): Try[EnvironmentArgsRecord] =
    tryCliArgs(args)
      .recoverWith({case x: Throwable => tryEnvironmentVars})

}
