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
      val documentVertex: PgxVertex[String] =
        keywordVerticesStream
          .lift(1)
          .get

      val analyst = graph.getSession.createAnalyst

      val personalizedPageRankProp = analyst.personalizedPagerank(
        graph, documentVertex, 0.00001, 0.85, 5000, true
      )

      val result = JavaConverters.asScalaIterator(
        personalizedPageRankProp
          .getTopKValues(take + 1)
          .iterator
      )
        .filter(graphVertex =>
          graphVertex.getKey.getProperty("type").equals(keyword)
        )
        .map(_.getKey.getId)
        .toList
      analyst.destroy()
      result
    }
  }

}
