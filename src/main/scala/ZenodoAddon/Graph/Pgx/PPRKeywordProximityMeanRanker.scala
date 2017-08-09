package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.KeywordProximityRanker
import ZenodoAddon.Graph.KeywordVertexFinder
import oracle.pgx.api.{PgxGraph, PgxVertex}
import scala.collection.JavaConverters


class PPRKeywordProximityMeanRanker extends
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

      val keywordsPerDocumentVertex = Math.pow(Math.max(Math.ceil(
        take.toDouble / keywordVerticesStream.size.toDouble
      ), 2.0), 2.0).toInt
      val neededDocumentVerticesStreamSize = Math.ceil(
        take.toDouble / (keywordsPerDocumentVertex - 1).toDouble
      ).toInt

      val analyst = graph.getSession.createAnalyst()

      val ranks = keywordVerticesStream
        .par
        .flatMap(documentVertex => {
          val personalizedPageRankProp =
            analyst.personalizedPagerank(
              graph, documentVertex, 0.00001, 0.85, 5000, true
            )

          val results = for {
            tuplet <- JavaConverters.asScalaIterator(
              personalizedPageRankProp
                .getTopKValues(keywordsPerDocumentVertex)
                .iterator
            )
            vertexType = tuplet.getKey.getProperty[String]("type")
            vertexId = tuplet.getKey.getId
            if vertexType.equals("keyword")
            if !vertexId.equalsIgnoreCase(keyword)
          } yield (vertexId, tuplet.getValue.toDouble)

          results.toList
        })
        .toList
        .sortBy(_._2)
        .reverse
        .map(_._1)
        .distinct
        .take(take)

      analyst.destroy()
      ranks
    }
  }

}
