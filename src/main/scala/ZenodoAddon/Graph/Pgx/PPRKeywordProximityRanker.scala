package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.{KeywordProximityRanker, KeywordVertexFinder}
import oracle.pgx.api.{PgxGraph, PgxVertex, VertexSet}

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
           keywords: List[String],
           keywordVertexFinder: KeywordVertexFinder[
             PgxVertex[String],
             PgxGraph
             ],
           take: Int) = {

    val keywordVerticesStreams = keywords.map(
      keyword => keywordVertexFinder.find(keyword, graph)
    )
    lazy val anyKeywordVerticesStreamEmpty =
      keywordVerticesStreams.foldLeft(false)(
        (condition, keywordVerticesStream) => {
          if (condition) condition
          else keywordVerticesStream.isEmpty
        }
      )

    if (keywordVerticesStreams.isEmpty) List()
    else if (anyKeywordVerticesStreamEmpty) List()
    else {
      val startingKeywordVertices: VertexSet[String] = {
        val keywordVerticesList =
          keywordVerticesStreams.map(_.iterator.next())

        val vertexSet = graph.createVertexSet[String]()
        vertexSet.addAll(keywordVerticesList.toArray)

        vertexSet
      }

      val analyst = graph.getSession.createAnalyst()

      val personalizedPageRankProp = analyst.personalizedPagerank(
        graph, startingKeywordVertices, 0.00001, 0.85, 5000, true
      )

      val resultsIterator = for {
        tuplet <- JavaConverters.asScalaIterator(
          personalizedPageRankProp
            .getTopKValues(take)
            .iterator
        )
        vertex = tuplet.getKey
        vertexType = vertex.getProperty[String]("type")
        vertexId = vertex.getId
        if !startingKeywordVertices.contains(vertex)
        if vertexType.equals("keyword")
      } yield vertexId
      val results = resultsIterator.toList

      analyst.destroy()
      results
    }
  }

}
