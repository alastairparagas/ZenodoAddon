package ZenodoAddon.Graph

import oracle.pgx.api.{PgxGraph, VertexSet, PgxVertex}
import oracle.pgx.api.filter.VertexFilter

import collection.JavaConverters


class PPRKeywordProximityRanker extends KeywordProximityRanker[PgxGraph]
{

  /**
    * Run Personalized Page Rank to see how well related a keyword is to
    * all the other keywords
    * @param graph: PgxGraph
    * @return Iterator[String, Double]
    */
  def rank(graph: PgxGraph, keyword: String) = {

    val keywordVertexIterator: Iterator[PgxVertex[String]] =
      JavaConverters.asScalaIterator(
        (graph.getVertices(new VertexFilter(
          "vertex.type == \"keyword\" && vertex.id == \"" + keyword + "\""
        )): VertexSet[String]).iterator()
      )

    if (!keywordVertexIterator.hasNext) Iterator()

    val keywordVertex = keywordVertexIterator.next()
    val analyst = graph.getSession.createAnalyst()
    val personalizedPageRankProp = analyst.personalizedPagerank(
      graph, keywordVertex, 0.001, 0.85, 1000, true
    )
    analyst.destroy()

    JavaConverters.asScalaIterator(
      (graph.createVertexSet(): VertexSet[String]).iterator()
    )
      .filter((someVertex) => !keywordVertex.equals(someVertex))
      .map((someVertex: PgxVertex[String]) =>
        (someVertex.getId,
          personalizedPageRankProp.get(someVertex).toDouble))
      .toSeq
      .sortBy(_._2)
  }

}
