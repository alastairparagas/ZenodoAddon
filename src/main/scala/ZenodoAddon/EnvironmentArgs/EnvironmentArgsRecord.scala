package ZenodoAddon.EnvironmentArgs


case class EnvironmentArgsRecord
(
  graphEngineDsn: String,
  graphSettingsFile: String,
  port: Integer,
  redisDsn: String,
  postgresDsn: String,
  postgresUser: String,
  postgresPassword: String,
  postgresDb: String
)
