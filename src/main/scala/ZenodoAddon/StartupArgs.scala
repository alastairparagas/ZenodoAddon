package ZenodoAddon

import scala.util.Try
import java.util.regex.Pattern

import org.rogach.scallop.ScallopConf


case class StartupArgRecord(
 graphEngineDsn: String,
 graphSettingsFile: String,
 port: Integer
)

/**
  * Wrapper for all the IO action we want to execute (reading
  * from environment variables or through passed program
  * arguments) where we can use such arguments to startup
  * the service
  */
class StartupArgs
{

  object StartupInputValidator
  {
    def validateDsn(dsnString: String): Boolean = {
      if (dsnString == null) {
        return false
      }
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
    def validateGraphSettingsFile(absFilePath: String): Boolean = {
      if (absFilePath == null) {
        return false
      }
      val pattern = Pattern.compile(
        "^\\/$|" +
          "(^(?=\\/)|^\\.|^\\.\\.)(\\/(?=[^/\0])" +
          "[^/\0]+)*\\/?$"
      )
      pattern.matcher(absFilePath).matches
    }
    def validatePort(port: String): Boolean = {
      try {
        Integer.parseInt(port)
        true
      } catch {
        case _: NumberFormatException => false
      }
    }
  }

  class CLIArgs(args: Seq[String]) extends ScallopConf(args)
  {
    val graphEngineDsn = opt[String](
      name = "graphEngineDsn",
      descr =
        "DSN of the Graph Engine Server, in the form " +
          "http://username:password@host:port/path",
      validate = StartupInputValidator.validateDsn,
      noshort = true,
      required = true
    )
    val graphSettingsFile = opt[String](
      name = "graphSettingsFile",
      descr =
        "Absolute file path of the Graph Settings File " +
          "on the PGX Server",
      validate = StartupInputValidator.validateGraphSettingsFile,
      noshort = true,
      required = true
    )
    val port = opt[String](
      name = "port",
      descr =
        "Port # where the API should listen on for requests",
      validate = StartupInputValidator.validatePort,
      noshort = true,
      required = true
    )

    errorMessageHandler = (errorMessage: String) => {
      throw new RuntimeException(errorMessage)
    }
  }

  def run(args: Array[String]): Try[StartupArgRecord] = {

    val tryCliArgs: Try[StartupArgRecord] = Try({
      val cliArgs = new CLIArgs(args)
      cliArgs.verify()

      StartupArgRecord(
        graphEngineDsn = cliArgs.graphEngineDsn.getOrElse(""),
        graphSettingsFile = cliArgs.graphSettingsFile.getOrElse(""),
        port = Integer.parseInt(cliArgs.port.getOrElse("80"))
      )
    })

    val tryEnvironmentVars: Try[StartupArgRecord] = Try({
      val graphEngineDsn = System.getenv("graphEngineDsn")
      val graphSettingsFile = System.getenv("graphSettingsFile")
      val port = System.getenv("port")

      (StartupInputValidator.validateDsn(
        graphEngineDsn
      ), StartupInputValidator.validateGraphSettingsFile(
        graphSettingsFile
      ), StartupInputValidator.validatePort(
        port
      )) match {
        case (true, true, true) => StartupArgRecord(
          graphEngineDsn = graphEngineDsn,
          graphSettingsFile = graphSettingsFile,
          port = Integer.parseInt(port)
        )
        case (_, _, _) => throw new RuntimeException(
          "Required program config (obtained from input parameters" +
            " or environment vars) are incorrectly provided or not provided" +
            " at all."
        )
      }
    })

    tryCliArgs
      .recoverWith({case x: Throwable => tryEnvironmentVars})
    
  }

}
