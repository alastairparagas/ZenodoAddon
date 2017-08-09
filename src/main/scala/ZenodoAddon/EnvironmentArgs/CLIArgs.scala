package ZenodoAddon.EnvironmentArgs

import org.rogach.scallop.ScallopConf


class CLIArgs(args: Seq[String]) extends ScallopConf(args)
{
  val graphEngineDsn = opt[String](
    name = "graphEngineDsn",
    descr =
      "DSN of the Graph Engine Server, in the form " +
        "http://username:password@host:port/path",
    validate = Validator.validateGraphEngineDsn,
    noshort = true,
    required = true
  )
  val graphSettingsFile = opt[String](
    name = "graphSettingsFile",
    descr =
      "Absolute file path of the Graph Settings File " +
        "on the PGX Server",
    validate = Validator.validateGraphSettingsFile,
    noshort = true,
    required = true
  )
  val port = opt[String](
    name = "port",
    descr =
      "Port # where the API should listen on for requests",
    validate = Validator.validatePort,
    noshort = true,
    required = true
  )
  val redisDsn = opt[String](
    name = "redisDsn",
    descr =
      "Redis DSN - [host]:[port]",
    validate = Validator.validateRedisDsn,
    noshort = true,
    required = true
  )
  val postgresDsn = opt[String](
    name = "postgresDsn",
    descr = "Postgres DSN - [host]:[port]",
    validate = Validator.validatePostgresDsn,
    noshort = true,
    required = true
  )
  val postgresUser = opt[String](
    name = "postgresUser",
    descr = "Postgres User",
    validate = Validator.validatePostgresUser,
    noshort = true,
    required = true
  )
  val postgresPassword = opt[String](
    name = "postgresPassword",
    descr = "Postgres Password",
    validate = Validator.validatePostgresPassword,
    noshort = true,
    required = true
  )
  val postgresDb = opt[String](
    name = "postgresDb",
    descr = "Postgres DB",
    validate = Validator.validatePostgresDb,
    noshort = true,
    required = true
  )

  errorMessageHandler = (errorMessage: String) => {
    throw new RuntimeException(errorMessage)
  }
}
