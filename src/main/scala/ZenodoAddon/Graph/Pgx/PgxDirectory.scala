package ZenodoAddon.Graph.Pgx

import ZenodoAddon.EnvironmentArgs.EnvironmentArgsRecord
import ZenodoAddon.Graph.{GraphNormalizer, KeywordProximityRanker, KeywordVertexFinder}
import oracle.pgx.api.{PgxGraph, PgxVertex}


class PgxDirectory(environmentArgsRecord: EnvironmentArgsRecord)
  extends AutoCloseable {

  private val keywordProximityRankerDirectory =
    Map[String, KeywordProximityRanker[PgxGraph, PgxVertex[String]]](
      "pprMean" -> new PPRMeanKeywordProximityRanker,
      "ppr" -> new PPRKeywordProximityRanker,
      "distance" -> new DistanceKeywordProximityRanker
    )

  private val keywordVertexFinderDirectory =
    Map[String, KeywordVertexFinder[PgxVertex[String], PgxGraph]](
      "plain" -> new PlainKeywordVertexFinder(environmentArgsRecord),
      "fulltext" -> new FullTextKeywordVertexFinder(environmentArgsRecord)
    )

  private val graphNormalizerDirectory =
    Map[String, GraphNormalizer[PgxGraph]](
      "lemma" -> new LemmaGraphNormalizer
    )

  def getKeywordProximityRanker(name: String):
  Option[KeywordProximityRanker[PgxGraph, PgxVertex[String]]] =
    keywordProximityRankerDirectory.get(name)

  def getKeywordVertexFinder(name: String):
  Option[KeywordVertexFinder[PgxVertex[String], PgxGraph]] =
    keywordVertexFinderDirectory.get(name)

  def getGraphNormalizer(name: String):
  Option[GraphNormalizer[PgxGraph]] =
    graphNormalizerDirectory.get(name)

  def close() = Unit

}
