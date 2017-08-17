package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph._
import oracle.pgx.api.{PgxGraph, PgxVertex}

class PgxRunner extends
  Runner[PgxGraph, PgxVertex[String]](new PgxSessionControl)
{

  def normalizeRequest(request: Request) = request match {
    case KeywordRecommendRequest(
    addons, keyword, ranker, vertexFinder, normalizerOption, take
    ) => {

      val directory = new PgxDirectory(environmentArgsRecord.get)

      val rankerInstanceOption =
        directory.getKeywordProximityRanker(ranker)
      val vertexFinderInstanceOption =
        directory.getKeywordVertexFinder(vertexFinder)
      val normalizerInstanceOption =
        normalizerOption.flatMap(directory.getGraphNormalizer)

      NKeywordRecommendRequest[PgxGraph, PgxVertex[String]](
        addons = addons,
        keyword = keyword,
        ranker = rankerInstanceOption,
        vertexFinder = vertexFinderInstanceOption,
        normalizer = normalizerInstanceOption,
        take = take
      )
    }
  }

}
