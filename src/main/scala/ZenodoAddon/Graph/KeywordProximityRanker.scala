package ZenodoAddon.Graph


trait KeywordProximityRanker[GraphTypeVar]
{
  def rank(pgxGraph: GraphTypeVar,
           keyword: String,
           take: Int): List[String]
}
