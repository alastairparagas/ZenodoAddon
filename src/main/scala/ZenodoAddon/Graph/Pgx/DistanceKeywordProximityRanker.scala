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
           keyword: List[String],
           keywordVertexFinder: KeywordVertexFinder[
             PgxVertex[String],
             PgxGraph
             ],
           take: Int) = {

    val keywordVerticesStreams = keyword.map(
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
      val startVertices: List[PgxVertex[String]] = keywordVerticesStreams
        .map(_.iterator.next())

      val analyst = graph.getSession.createAnalyst()

      val resultsList = startVertices
        .par
        .map(startVertex => {
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
                .getBottomKValues(Math.pow(take.toDouble, 2.0).toInt)
                .iterator
            )
            vertexId = tuplet.getKey.getId
            if !vertexId.equalsIgnoreCase(startVertex.getId)
          } yield vertexId
          val results = resultsIterator.toList
          undirectedGraph.destroy()

          results
        })
        .toList

      analyst.destroy()

      resultsList
        .reduce((resultList1, resultList2) =>
          resultList1.filter(resultList2.contains(_))
        )
        .take(take)
    }
  }

}
