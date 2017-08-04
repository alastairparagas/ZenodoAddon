package ZenodoAddon.Graph.QueryAddons

import ZenodoAddon.EnvironmentArgsRecord


class AddonsDirectory(environmentArgs: EnvironmentArgsRecord)
  extends AutoCloseable {

  val directory = Map[String, QueryAddon](
    "cache" -> new CacheAddon(environmentArgs)
  )

  def getAddon(name: String): Option[(QueryAddon, String)] =
    directory.get(name).map(queryAddon => (queryAddon, name))

  def close() = directory.foreach[Unit](_._2.close)

}
