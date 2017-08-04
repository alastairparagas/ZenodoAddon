package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.KeywordProximityRanker
import oracle.pgx.api.{PgqlResult, PgxGraph, PgxVertex}

import scala.collection.JavaConverters


class PPRKeywordProximityMeanRanker extends KeywordProximityRanker[PgxGraph]
{

  /**
    * Run Personalized Page Rank to see how well related a keyword is to
    * all the other keywords
    * @param graph: PgxGraph
    * @return Iterator[String, Double]
    */
  def rank(graph: PgxGraph, keyword: String, take: Int) = {

    val pgqlResultIterator: Iterator[PgqlResult] = {
      val pgqlResultIterator = JavaConverters.asScalaIterator(
        graph.queryPgql(
          "SELECT z " +
            "WHERE " +
            "(x WITH type='keyword' AND id() = '" + keyword + "')" +
            " <-- (z WITH type='document')"
        ).getResults.iterator
      )

      if (pgqlResultIterator.hasNext) pgqlResultIterator
      else {
        JavaConverters.asScalaIterator(
          graph.queryPgql(
            "SELECT z " +
              "WHERE " +
              "(x WITH type='keyword' AND id() =~ '" + keyword + "')" +
              " <-- (z WITH type='document' AND outDegree() >= 2)"
          ).getResults.iterator
        )
      }
    }

    if (pgqlResultIterator.isEmpty) List()
    else {
      val documentVerticesStream: Stream[PgxVertex[String]] =
        pgqlResultIterator.map(pgqlResult => {
          pgqlResult.getVertex("z"): PgxVertex[String]
        })
          .toStream

      val keywordsPerDocumentVertex = Math.pow(Math.max(Math.ceil(
        take.toDouble / documentVerticesStream.size.toDouble
      ), 2.0), 2.0).toInt
      val neededDocumentVerticesStreamSize = Math.ceil(
        take.toDouble / (keywordsPerDocumentVertex - 1).toDouble
      ).toInt

      val analyst = graph.getSession.createAnalyst()

      val ranks = documentVerticesStream
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
