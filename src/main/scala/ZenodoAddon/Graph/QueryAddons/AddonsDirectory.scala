package ZenodoAddon.Graph.QueryAddons

import ZenodoAddon.EnvironmentArgs.EnvironmentArgsRecord


class AddonsDirectory(environmentArgs: EnvironmentArgsRecord)
  extends AutoCloseable {

  val directory = Map[String, QueryAddon](
    "cache" -> new CacheAddon(environmentArgs)
  )

  def getAddon(name: String): Option[(QueryAddon, String)] =
    directory.get(name).map(queryAddon => (queryAddon, name))

  def allAddons(): List[QueryAddon] =
    directory.values.toList

  def close() = directory.values.foreach(_.close)

}
