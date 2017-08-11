package ZenodoAddon.Graph


trait KeywordProximityRanker[GraphTypeVar, VertexTypeVar]
{
  def rank(pgxGraph: GraphTypeVar,
           keyword: List[String],
           keywordVertexFinder: KeywordVertexFinder[
             VertexTypeVar,
             GraphTypeVar],
           take: Int): List[String]
}
