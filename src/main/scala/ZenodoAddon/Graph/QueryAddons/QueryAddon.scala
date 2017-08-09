package ZenodoAddon.Graph.QueryAddons

import ZenodoAddon.Graph.{
  NKeywordRecommendRequest,
  KeywordRecommendResponse
}


/**
  * QueryAddon overlays over a query execution, allowing us to add
  * features besides simply querying the underlying graph engine
  */
trait QueryAddon extends AutoCloseable
{

  def pipeline(requestPacket: NKeywordRecommendRequest[_, _],
               queryExecution: () => KeywordRecommendResponse):
  (KeywordRecommendResponse, Map[String, String])

  def unconditionalPipeline(requestPacket: NKeywordRecommendRequest[_, _],
                            queryExecution: () => KeywordRecommendResponse):
  KeywordRecommendResponse

}
