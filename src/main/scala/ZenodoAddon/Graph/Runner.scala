package ZenodoAddon.Graph

import oracle.pgx.api.PgxGraph

import scala.util.Try

sealed trait Request
case class KeywordRecommendRequest(
  normalizer: Option[String],
  ranker: String,
  recommendationCount: Int = 5,
  keyword: String
) extends Request

sealed trait NRequest
case class NKeywordRecommendRequest(
 normalizer: Option[GraphNormalizer[PgxGraph]],
 ranker: Option[KeywordProximityRanker[PgxGraph]],
 recommendationCount: Int,
 keyword: String
) extends NRequest

sealed trait Response
case class KeywordRecommendResponse(
 isSuccessful: Boolean,
 message: Option[String],
 result: Option[List[String]]
) extends Response

/**
  * Request objects are submitted to the Runner and
  * Response objects are returned.
  *
  * The Runner encapsulates everything about graph querying.
  * Above this step, no knowledge about any graph engine implementation
  * should leak out. This is in essence, the "main" of the Graph
  * package
  */
class Runner
{
  private val sessionControl = new PgxSessionControl

  def startup(graphEngineDsn: String,
              graphSettingsFilePath: String) = Try({
    sessionControl.initializeSession(graphEngineDsn)
    sessionControl.loadSettings(graphSettingsFilePath)
  })

  def query(request: Request) = Try({
    def getClassObjectFromString(className: String,
                                 traitClass: Class[_]): Option[Class[_]] = {
      try {
        val classObject = Class.forName(className)

        if (traitClass.isAssignableFrom(classObject)) Some(classObject)
        else None
      } catch {
        case _:ClassNotFoundException => None
      }
    }
    def normalizeRequest(request: Request): NRequest = request match {
      case KeywordRecommendRequest(
        normalizerOption,
        ranker,
        recommendationCount,
        keyword
      ) => {
        val normalizerInstOpt: Option[GraphNormalizer[PgxGraph]] =
          normalizerOption
            .flatMap(normalizer => getClassObjectFromString(
              normalizer,
              classOf[GraphNormalizer[PgxGraph]]
            ))
            .map(normalizerClass => normalizerClass
              .newInstance
              .asInstanceOf[GraphNormalizer[PgxGraph]])
        val rankerInstOpt: Option[KeywordProximityRanker[PgxGraph]] =
        {
          val rankerClassOption = getClassObjectFromString(
            ranker,
            classOf[KeywordProximityRanker[PgxGraph]]
          )

          rankerClassOption.map(rankerClass => rankerClass
            .newInstance
            .asInstanceOf[KeywordProximityRanker[PgxGraph]]
          )
        }

        NKeywordRecommendRequest(
          normalizerInstOpt, rankerInstOpt,
          recommendationCount, keyword
        )
      }
    }

    normalizeRequest(request) match {
      case NKeywordRecommendRequest(_, None, _, _) =>
        KeywordRecommendResponse(
          isSuccessful = false,
          Some("No valid ranker execution type provided"),
          None
        )
      case NKeywordRecommendRequest(Some(n), Some(r), take, keyword) => {
        sessionControl.transformGraph(n.normalize)
        val result = r.rank(sessionControl.getGraph, keyword)
          .toList
          .take(take)
          .map(tuplet => tuplet._1)
        KeywordRecommendResponse(
          isSuccessful = true,
          Some("Success"),
          Some(result)
        )
      }
      case NKeywordRecommendRequest(None, Some(r), take, keyword) => {
        val result = r.rank(sessionControl.getGraph, keyword)
          .toList
          .take(take)
          .map(tuplet => tuplet._1)
        KeywordRecommendResponse(
          isSuccessful = true,
          Some("Success"),
          Some(result)
        )
      }
    }
  })

  def shutdown() = Try({
    sessionControl.destroySession()
  })

}
