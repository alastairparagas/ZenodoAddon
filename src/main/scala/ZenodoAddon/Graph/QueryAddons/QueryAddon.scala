package ZenodoAddon.Graph.QueryAddons

import ZenodoAddon.Graph.{
  NRequest,
  Response
}

/**
  * QueryAddon overlays over a query execution, allowing us to add
  * features besides simply querying the underlying graph engine
  */
trait QueryAddon extends AutoCloseable
{

  def pipeline[
    RequestSubtype <: NRequest[_],
    ResponseSubtype <: Response
  ](requestPacket: RequestSubtype,
    queryExecution: () => ResponseSubtype):
  (ResponseSubtype, Map[String, String])

}
