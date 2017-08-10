package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.{KeywordProximityRanker, KeywordVertexFinder}
import oracle.pgx.api.{PgxGraph, PgxVertex}

import scala.collection.JavaConverters


class PPRKeywordProximityRanker extends
  KeywordProximityRanker[PgxGraph, PgxVertex[String]]
{

  /**
    * Run Personalized Page Rank to see how well related a keyword is to
    * all the other keywords
    * @param graph: PgxGraph
    * @return List[String]
    */
  def rank(graph: PgxGraph,
           keyword: String,
           keywordVertexFinder: KeywordVertexFinder[
             PgxVertex[String],
             PgxGraph
             ],
           take: Int) = {

    val keywordVerticesStream = keywordVertexFinder.find(keyword, graph)

    if (keywordVerticesStream.isEmpty) List()
    else {
      val keywordVertex: PgxVertex[String] =
        keywordVerticesStream.iterator.next()

      val analyst = graph.getSession.createAnalyst()

      val personalizedPageRankProp = analyst.personalizedPagerank(
        graph, keywordVertex, 0.00001, 0.85, 5000, true
      )

      val resultsIterator = for {
        tuplet <- JavaConverters.asScalaIterator(
          personalizedPageRankProp
            .getTopKValues(take + 1)
            .iterator
        )
        vertexType = tuplet.getKey.getProperty[String]("type")
        vertexId = tuplet.getKey.getId
        if vertexType.equals("keyword")
        if !vertexId.equalsIgnoreCase(keyword)
      } yield vertexId
      val results = resultsIterator.toList

      analyst.destroy()
      results
    }
  }

}
