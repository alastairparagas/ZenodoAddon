package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.KeywordProximityRanker
import ZenodoAddon.Graph.KeywordVertexFinder
import oracle.pgx.api.{PgxGraph, PgxVertex, VertexSet}

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

      val keywordVertexSetsList: List[VertexSet[String]] = {
        val smallestKeywordVerticesStreamSize =
          keywordVerticesStreams.foldLeft(Integer.MAX_VALUE)(
            (smallestSize, currentStream) => {
              if (currentStream.size < smallestSize) currentStream.size
              else smallestSize
            })

        keywordVerticesStreams
          .map(_.take(smallestKeywordVerticesStreamSize).toList)
          .transpose
          .map(vertices => {
            val vertexSet = graph.createVertexSet[String]()
            vertexSet.addAll(vertices.toArray)

            vertexSet
          })
      }

      val keywordsPerDocumentVertex = Math.pow(Math.max(Math.ceil(
        take.toDouble / keywordVertexSetsList.size.toDouble
      ), 1), 2).toInt
      val analyst = graph.getSession.createAnalyst()

      val ranks = keywordVertexSetsList
        .par
        .flatMap(keywordVertexSet => {
          val personalizedPageRankProp =
            analyst.personalizedPagerank(
              graph, keywordVertexSet, 0.00001, 0.85, 5000, true
            )

          val results = for {
            tuplet <- JavaConverters.asScalaIterator(
              personalizedPageRankProp
                .getTopKValues(keywordsPerDocumentVertex)
                .iterator
            )
            vertex = tuplet.getKey
            vertexType = vertex.getProperty[String]("type")
            vertexId = vertex.getId
            if !keywordVertexSet.contains(vertex)
            if vertexType.equals("keyword")
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
