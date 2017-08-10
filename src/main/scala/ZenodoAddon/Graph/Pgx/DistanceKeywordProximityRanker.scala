package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.{KeywordProximityRanker, KeywordVertexFinder}
import oracle.pgx.api.filter.VertexFilter
import oracle.pgx.api.{PgxGraph, PgxVertex}
import oracle.pgx.common.types.PropertyType

import scala.collection.JavaConverters


class DistanceKeywordProximityRanker extends
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
      val startVertex: PgxVertex[String] =
        keywordVerticesStream.iterator.next()

      val analyst = graph.getSession.createAnalyst()
      val undirectedGraph = graph.undirect()

      analyst.filteredBfs[String](
        undirectedGraph,
        startVertex,
        new VertexFilter("true"),
        new VertexFilter("true"),
        true,
        undirectedGraph.createVertexProperty[String, java.lang.Integer](
          PropertyType.INTEGER, "dist"
        ),
        undirectedGraph.createVertexProperty[String, PgxVertex[String]](
          PropertyType.VERTEX, "prev"
        )
      )

      val distanceProp = undirectedGraph
        .filter(new VertexFilter("vertex.type == 'keyword'"))
        .getVertexProperty[String, java.lang.Integer]("dist")

      val resultsIterator = for {
        tuplet <- JavaConverters.asScalaIterator(
          distanceProp
            .getBottomKValues(take)
            .iterator
        )
        vertexId = tuplet.getKey.getId
        if !vertexId.equalsIgnoreCase(keyword)
      } yield vertexId
      val results = resultsIterator.toList

      undirectedGraph.destroy()
      analyst.destroy()
      results
    }
  }

}
