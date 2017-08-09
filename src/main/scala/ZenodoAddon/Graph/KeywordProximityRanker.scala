package ZenodoAddon.Graph


trait KeywordProximityRanker[GraphTypeVar, VertexTypeVar]
{
  def rank(pgxGraph: GraphTypeVar,
           keyword: String,
           keywordVertexFinder: KeywordVertexFinder[
             VertexTypeVar,
             GraphTypeVar],
           take: Int): List[String]
}
