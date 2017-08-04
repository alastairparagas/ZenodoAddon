package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.KeywordProximityRanker
import oracle.pgx.api.{PgqlResult, PgxGraph, PgxVertex}

import scala.collection.JavaConverters


class PPRKeywordProximityRanker extends KeywordProximityRanker[PgxGraph]
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
      val documentVertex: PgxVertex[String] =
        pgqlResultIterator
          .next
          .getVertex("z")

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
