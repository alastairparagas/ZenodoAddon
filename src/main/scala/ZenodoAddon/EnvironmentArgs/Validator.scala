package ZenodoAddon.EnvironmentArgs

import java.util.regex.Pattern


object Validator
{

  def validateGraphEngineDsn(dsnString: String): Boolean = {
    if (dsnString == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "http:\\/\\/" +
          "[^:/?#\\[\\]@]*" +
          ":[^:/?#\\[\\]@]*" +
          "@[a-zA-Z0-9.]+" +
          ":\\d+" +
          "[/]*[^:?#\\[\\]@]*"
      )
      pattern.matcher(dsnString).matches
    }
  }

  def validateGraphSettingsFile(absFilePath: String): Boolean = {
    if (absFilePath == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "^\\/$|" +
          "(^(?=\\/)|^\\.|^\\.\\.)(\\/(?=[^/\u0000])" +
          "[^/\u0000]+)*\\/?$"
      )
      pattern.matcher(absFilePath).matches
    }
  }

  def validatePort(port: String): Boolean = {
    try {
      Integer.parseInt(port)
      true
    } catch {
      case _: NumberFormatException => false
    }
  }

  def validateRedisDsn(redisDsn: String): Boolean = {
    if (redisDsn == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "[a-zA-Z0-9.]+:\\d+"
      )
      pattern.matcher(redisDsn).matches
    }
  }

  def validatePostgresDsn(postgresDsn: String): Boolean = {
    if (postgresDsn == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "[a-zA-Z0-9.]+:\\d+"
      )
      pattern.matcher(postgresDsn).matches
    }
  }

  def validatePostgresUser(postgresUser: String): Boolean = {
    if (postgresUser == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "^\\s*\\S+\\s*$"
      )
      pattern.matcher(postgresUser).matches
    }
  }

  def validatePostgresPassword(postgresPassword: String): Boolean = {
    if (postgresPassword == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "^\\s*\\S+\\s*$"
      )
      pattern.matcher(postgresPassword).matches
    }
  }

  def validatePostgresDb(postgresDb: String): Boolean = {
    if (postgresDb == null) {
      false
    } else {
      val pattern = Pattern.compile(
        "^\\s*\\S+\\s*$"
      )
      pattern.matcher(postgresDb).matches
    }
  }

}
