package ZenodoAddon.Graph.QueryAddons

import java.time.Instant

import ZenodoAddon.Graph.{
  NRequest,
  NKeywordRecommendRequest,
  Response,
  KeywordRecommendResponse
}
import ZenodoAddon.EnvironmentArgsRecord
import com.redis.RedisClientPool

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class CacheAddon(environmentArgs: EnvironmentArgsRecord) extends QueryAddon
{

  private var redisPoolOption: Option[RedisClientPool] = None

  environmentArgs.redisDsn.split(":").take(2) match {
    case Array(hostname, port) => {
      redisPoolOption = Some(new RedisClientPool(
        hostname, Integer.parseInt(port)
      ))
    }
    case _ => throw new RuntimeException(
      "Require Redis settings"
    )
  }

  def pipeline[RequestSubtype <: NRequest[_], ResponseSubtype <: Response]
  (
    requestPacket: RequestSubtype,
    queryExecution: () => ResponseSubtype
  ):
  (ResponseSubtype, Map[String, String]) = {
    (requestPacket, queryExecution) match {
      case (
        rp: NKeywordRecommendRequest[_],
        qe: Function0[KeywordRecommendResponse]) =>
        pipeline(rp, qe)

      case(_, _) => (queryExecution.apply(), Map[String, String]())
    }
  }

  def pipeline
  (
    requestPacket: NKeywordRecommendRequest[_],
    queryExecution: Function0[KeywordRecommendResponse]
  ): (KeywordRecommendResponse, Map[String, String]) = {

    val keyword = requestPacket.keyword
    val ranker = requestPacket.ranker.get
    val take = requestPacket.take

    def cacheResultInRedis(results: List[String]) = Future {
      redisPoolOption.get.withClient(client => {
        val cacheKey =
          s"krr,${keyword},${ranker.getClass.getName},${take}"
        results.foreach(result => client.rpush(
          cacheKey, result
        ))

        client.hmset("cache-ages", Map(
          cacheKey -> Instant.now().toEpochMilli
        ))
      })
    }

    val (cachedResults, cacheAge) = {
      var cachedResultOption: Option[List[Option[String]]] = None
      var cachedResultAgeOption: Option[Long] = None

      redisPoolOption.get.withClient(client => {
        val scanResults = client.scan(
          0,
          s"krr,${keyword},${ranker.getClass.getName},*",
          count = take
        )

        val (_, matchedKeys) = scanResults.getOrElse((None, None))

        val cacheKeyOption = matchedKeys
          .getOrElse(List())
          .flatten
          .filter(keyname =>
            Integer.parseInt(keyname.split(","){3}) >= take
          )
          .lift(0)

        cachedResultOption = cacheKeyOption.flatMap(
          cacheKeyString => client.lrange(cacheKeyString, 0, take)
        )
        cachedResultAgeOption = cacheKeyOption
          .flatMap(
            cacheKeyString => client
              .hmget[String, String]("cache-ages", cacheKeyString)
              .flatMap(_.get(cacheKeyString))
          )
          .map(_.toLong)
      })

      (cachedResultOption
        .getOrElse(List())
        .flatten
        .take(take)
        ,
        cachedResultAgeOption
          .getOrElse(0L))
    }

    if (cachedResults.isEmpty) {
      val queryExecutionResponse = queryExecution()
      queryExecutionResponse.result.map(results => cacheResultInRedis(results))
      (queryExecutionResponse, Map("cache-age" -> cacheAge.toString))
    } else (
      KeywordRecommendResponse(
        isSuccessful = true,
        message = Some("Success"),
        result = Some(cachedResults)
      ), Map("cache-age" -> cacheAge.toString))
  }

  def close() = redisPoolOption.map(_.close).get

}
