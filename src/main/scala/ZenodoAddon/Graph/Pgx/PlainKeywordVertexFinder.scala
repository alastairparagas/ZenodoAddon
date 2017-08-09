package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.KeywordVertexFinder
import oracle.pgx.api.{PgxGraph, PgxVertex}

import scala.collection.JavaConverters


class PlainKeywordVertexFinder extends
  KeywordVertexFinder[PgxVertex[String], PgxGraph]
{

  def find(keyword: String, graph: PgxGraph) = {

    val pgqlResultIterator = JavaConverters.asScalaIterator(
      graph.queryPgql(
        "SELECT x " +
          "WHERE " +
          "(x WITH type='keyword' AND id() = '" + keyword + "')"
        ).getResults.iterator
    )

    if (pgqlResultIterator.hasNext)
      pgqlResultIterator
        .toStream
        .map(pgqlResult => {
          pgqlResult.getVertex("x"): PgxVertex[String]
        })
    else
      JavaConverters.asScalaIterator(
        graph.queryPgql(
          "SELECT d " +
            "WHERE " +
            "(x WITH type='keyword' AND id() =~ '" + keyword + "')"
        ).getResults.iterator
      )
        .toStream
        .map(pgqlResult =>
          pgqlResult.getVertex("x"): PgxVertex[String]
        )

  }

}
