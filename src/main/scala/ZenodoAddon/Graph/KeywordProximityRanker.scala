package ZenodoAddon.Graph


trait KeywordProximityRanker[GraphTypeVar]
{
  def rank(pgxGraph: GraphTypeVar,
           keyword: String): Seq[(String, Double)]
}
