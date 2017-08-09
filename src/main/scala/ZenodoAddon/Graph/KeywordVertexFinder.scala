package ZenodoAddon.Graph


trait KeywordVertexFinder[VertexTypeVar, GraphTypeVar]
{

  def find(keyword: String, graph: GraphTypeVar): Stream[VertexTypeVar]

}
