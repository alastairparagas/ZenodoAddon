package ZenodoAddon.Graph

import ZenodoAddon.EnvironmentArgs.EnvironmentArgsRecord
import ZenodoAddon.Graph.QueryAddons.AddonsDirectory

import scala.util.Try


abstract class Request(
  addons: List[String]
)
case class KeywordRecommendRequest(
  addons: List[String],
  keyword: List[String],
  ranker: String,
  vertexFinder: String,
  normalizer: Option[String],
  take: Int
) extends Request(addons)

abstract class NRequest[GraphType, VertexType](
  addons: List[String]
)
case class NKeywordRecommendRequest[GraphType, VertexType]
(
  addons: List[String],
  keyword: List[String],
  ranker: Option[KeywordProximityRanker[GraphType, VertexType]],
  vertexFinder: Option[KeywordVertexFinder[VertexType, GraphType]],
  normalizer: Option[GraphNormalizer[GraphType]],
  take: Int
) extends NRequest[GraphType, VertexType](addons)

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
abstract class Runner[GraphType, VertexType]
(
  sessionControl: SessionControl[_, GraphType]
) extends AutoCloseable
{

  protected var addonsDirectory: Option[AddonsDirectory] = None
  protected var environmentArgsRecord: Option[EnvironmentArgsRecord] = None

  def startup(environmentArgs: EnvironmentArgsRecord) = Try({
    sessionControl.initializeSession(environmentArgs.graphEngineDsn)
    sessionControl.loadSettings(environmentArgs.graphSettingsFile)
    addonsDirectory = Some(new AddonsDirectory(environmentArgs))
    environmentArgsRecord = Some(environmentArgs)
  })

  protected def normalizeRequest(request: Request):
  NRequest[GraphType, VertexType]

  private def addonOverlayKeywordRecommendRequest
  (
    requestPacket: NKeywordRecommendRequest[_, _],
    originalExecution: () => KeywordRecommendResponse
  ): KeywordRecommendResponse = {
    val addonsDirectoryInstance = addonsDirectory.get

    val overlayedExecution = addonsDirectoryInstance
      .allAddons()
      .foldLeft(originalExecution)(
        (previousExecution, addon) =>
          () => addon.unconditionalPipeline(
            requestPacket,
            previousExecution
          )
      )

    val (composedExecution, addonsMetadata) = requestPacket.addons
      .flatMap(addonsDirectoryInstance.getAddon)
      .foldLeft((overlayedExecution, Map[String, Map[String, String]]()))(
        (accumulated, queryAddonTuple) => {
          val (composedExecution, addonResultsMetadata) = accumulated
          val (queryAddonObject, queryAddonName) = queryAddonTuple
          val (newComposedExecution, addonResultMetadata) =
            queryAddonObject.pipeline(requestPacket, composedExecution)
          (() => newComposedExecution,
            addonResultsMetadata + (queryAddonName -> addonResultMetadata))
        })

    val keywordRecommendResponse = composedExecution()
    keywordRecommendResponse.copy(addonsMetadata = addonsMetadata)
  }

  def query(request: Request) = Try({
    normalizeRequest(request) match {
      case NKeywordRecommendRequest(
        _, _, None, _, _, _
      ) =>
        KeywordRecommendResponse(
          isSuccessful = false,
          message = Some("No valid ranker execution type provided"),
          result = None
        )

      case NKeywordRecommendRequest(
        _, _, _, None, _, _
      ) =>
        KeywordRecommendResponse(
          isSuccessful = false,
          message = Some("No valid vertex finder provided"),
          result = None
        )

      case requestPacket @ NKeywordRecommendRequest(
        addons, keyword, Some(ranker),
        Some(vertexFinder), Some(normalizer), take
      ) =>
        addonOverlayKeywordRecommendRequest(requestPacket, () => {
          sessionControl.transformGraph(normalizer)

          val result = ranker.rank(
            sessionControl.getGraph,
            keyword,
            vertexFinder,
            take
          )
          KeywordRecommendResponse(
            isSuccessful = true,
            message = Some("Success"),
            result = Some(result)
          )
        })

      case requestPacket @ NKeywordRecommendRequest(
        addons, keyword, Some(ranker),
        Some(vertexFinder), None, take
      ) =>
        addonOverlayKeywordRecommendRequest(requestPacket, () => {
          val results = ranker.rank(
            sessionControl.getGraph,
            keyword,
            vertexFinder,
            take
          )

          KeywordRecommendResponse(
            isSuccessful = true,
            message = Some("Success"),
            result = Some(results)
          )
        })
    }
  })

  def close() = {
    addonsDirectory.get.close()
    sessionControl.destroySession()
  }

}
