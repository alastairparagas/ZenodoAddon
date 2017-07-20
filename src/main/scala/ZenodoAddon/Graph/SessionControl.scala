package ZenodoAddon.Graph


trait SessionControl[SessionTypeVar, GraphTypeVar]
{
  def initializeSession(dsn: String): SessionTypeVar
  def loadSettings(settingsFilePath: String): Unit
  def transformGraph(transformer: GraphTypeVar => GraphTypeVar): Unit
  def getGraph: GraphTypeVar
  def destroySession(): Unit
}
