package ZenodoAddon.Graph.Pgx

import ZenodoAddon.EnvironmentArgs.EnvironmentArgsRecord
import ZenodoAddon.Graph.KeywordVertexFinder
import oracle.pgx.api.{PgxGraph, PgxVertex}

import scala.collection.JavaConverters
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import com.typesafe.config.ConfigFactory
import cz.alenkacz.db.postgresscala._


class FullTextKeywordVertexFinder(environmentArgsRecord: EnvironmentArgsRecord)
  extends KeywordVertexFinder[PgxVertex[String], PgxGraph]
{

  implicit val connection: Future[Connection] =
    PostgresConnection.fromConfig(
      ConfigFactory.parseString("connectionString=\"jdbc:postgresql://" + s"${
        environmentArgsRecord.postgresDsn
      }'/${
        environmentArgsRecord.postgresDb
      }?user=${
        environmentArgsRecord.postgresUser
      }&password=${
        environmentArgsRecord.postgresPassword
      }" + "\"")
    )

  def find(keyword: String, graph: PgxGraph) = {

    val matchingKeywordFuture: Future[Stream[PgxVertex[String]]] = for {
      connectionObject <- connection
      sqlResult <-
      sql"SELECT keyword, rank FROM search_keyword_matches($keyword)"
        .query(
          row => (row(0).string, row(1).any.toString.toDouble)
        )(connectionObject)

      matchingKeywordStream = sqlResult
        .take(1)
        .map(_._1)
        .lift(0)
        .map(matchingKeyword => {
          JavaConverters.asScalaIterator(graph.queryPgql(
            "SELECT x " +
              "WHERE " +
              "(x WITH type='keyword' AND id() = '" + matchingKeyword + "')"
          ).getResults.iterator)
            .toStream
            .map(_.getVertex("x") : PgxVertex[String])
        })
        .getOrElse(Stream())
    } yield matchingKeywordStream

    Await.result[Stream[PgxVertex[String]]](matchingKeywordFuture, 20.seconds)

  }

}
