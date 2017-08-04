package ZenodoAddon.Graph

import ZenodoAddon.Graph.QueryAddons.AddonsDirectory
import ZenodoAddon.{EnvironmentArgsRecord, Utils}
import scala.util.Try


abstract class Request(
  addons: List[String]
)
case class KeywordRecommendRequest(
  addons: List[String],
  keyword: String,
  ranker: String,
  normalizer: Option[String],
  take: Int
) extends Request(addons)

abstract class NRequest[GraphType](
  addons: List[String]
)
case class NKeywordRecommendRequest[GraphType]
(
  addons: List[String],
  keyword: String,
  ranker: Option[KeywordProximityRanker[GraphType]],
  normalizer: Option[GraphNormalizer[GraphType]],
  take: Int
) extends NRequest[GraphType](addons)

sealed trait Response
case class KeywordRecommendResponse(
 isSuccessful: Boolean,
 message: Option[String],
 addonsMetadata: Map[String, Map[String, String]] = Map(),
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
  *
  * Different Graph Runners could be created for different graph engines,
  * not just PGX. This allows even for a runtime running of different
  * graph engines - not just PGX. All that is required is:
  *
  * GraphType - type used by the graph engine to represent a graph
  * SessionControl - an instance that implements the SessionControl trait,
  *   which encapsulates a graph querying/manipulation session
  */
abstract class Runner[GraphType]
(
  sessionControl: SessionControl[_, GraphType]
) extends AutoCloseable
{

  private var addonsDirectory: Option[AddonsDirectory] = None

  def startup(environmentArgs: EnvironmentArgsRecord) = Try({
    sessionControl.initializeSession(environmentArgs.graphEngineDsn)
    sessionControl.loadSettings(environmentArgs.graphSettingsFile)
    addonsDirectory = Some(new AddonsDirectory(environmentArgs))
  })

  private def normalizeRequest(request: Request): NRequest[GraphType] =
    request match {
      case KeywordRecommendRequest(
        addons, keyword, ranker, normalizerOption, take
      ) => {
        val normalizerInstanceOption =
          normalizerOption.flatMap(
            Utils.getInstanceObjectFromString[
                GraphNormalizer[GraphType]
              ]
          )
        val rankerInstanceOption =
          Utils.getInstanceObjectFromString[
              KeywordProximityRanker[GraphType]
            ](ranker)

        NKeywordRecommendRequest[GraphType](
          addons = addons,
          keyword = keyword,
          ranker = rankerInstanceOption,
          normalizer = normalizerInstanceOption,
          take = take
        )
      }
    }

  def query(request: Request) = Try({
    normalizeRequest(request) match {
      case NKeywordRecommendRequest(
        _, _, None, _, _
      ) =>
        KeywordRecommendResponse(
          isSuccessful = false,
          message = Some("No valid ranker execution type provided"),
          result = None
        )

      case NKeywordRecommendRequest(
        addons, keyword, Some(ranker), Some(normalizer), take
      ) =>
        sessionControl.transformGraph(normalizer)
        val result = ranker.rank(sessionControl.getGraph, keyword, take)
        KeywordRecommendResponse(
          isSuccessful = true,
          message = Some("Success"),
          result = Some(result)
        )

      case requestPacket @ NKeywordRecommendRequest(
        addons, keyword, Some(ranker), None, take
      ) =>
        val originalExecution: () => KeywordRecommendResponse = () => {
          val results = ranker.rank(sessionControl.getGraph, keyword, take)
          KeywordRecommendResponse(
            isSuccessful = true,
            message = Some("Success"),
            result = Some(results)
          )
        }

        val (composedExecution, addonsMetadata) = addons
          .flatMap(addonsDirectory.get.getAddon(_))
          .foldLeft((originalExecution, Map[String, Map[String, String]]()))(
            (accumulated, queryAddonTuple) => {
              val (composedExecution, addonResultsMetadata) = accumulated
              val (queryAddonObject, queryAddonName) = queryAddonTuple
              val (newComposedExecution, addonResultMetadata) =
                queryAddonObject.pipeline(requestPacket, composedExecution)
              (() => newComposedExecution,
                addonResultsMetadata + (queryAddonName -> addonResultMetadata))
          })

        val keywordRecommendResponse = composedExecution()
        keywordRecommendResponse.copy(
          addonsMetadata = addonsMetadata
        )
    }
  })

  def close() = {
    addonsDirectory.get.close()
    sessionControl.destroySession()
  }

}
