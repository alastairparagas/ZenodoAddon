package ZenodoAddon.Graph.QueryAddons

import ZenodoAddon.Graph.{KeywordProximityRanker, KeywordRecommendResponse, KeywordVertexFinder, NKeywordRecommendRequest}
import java.time.Instant

import ZenodoAddon.EnvironmentArgs.EnvironmentArgsRecord

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.redis.RedisClientPool


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

  private def cacheKeywordRecommendationInRedis
  (
    keyword: List[String],
    ranker: KeywordProximityRanker[_, _],
    vertexFinder: KeywordVertexFinder[_, _],
    take: Int,
    results: List[String]
  ): Unit = Future {

    redisPoolOption.get.withClient(client => {

      // Create a new cache entry for the fresh computation output and put
      //    the current cache entry's timestamp of entry into the 'cache-ages'
      //    hashmap.
      val insertResultToCache = () => {
        val cacheKey =
          s"krr,${keyword.sorted.mkString(".")}," +
            s"${ranker.getClass.getName}," +
            s"${vertexFinder.getClass.getName},${take}"
        results.foreach(result => client.rpush(cacheKey, result))
        client.hmset("cache-ages", Map(cacheKey -> Instant.now().toEpochMilli))
      }

      // Delete cache entries with lower take value as well as those cache
      //    entries' timestamp in the 'cache-ages' hashmap
      val cacheKeysList: List[String] = {
        val cacheKeysPattern =
          s"krr,${keyword.sorted.mkString(".")}," +
            s"${ranker.getClass.getName},${vertexFinder.getClass.getName}*"

        val scanResults = client.scan(0, cacheKeysPattern, take)
        val (_, matchedKeys) = scanResults.getOrElse((None, None))

        matchedKeys
          .getOrElse(List())
          .flatten
      }

      val cacheKeyAndTakeTuples = cacheKeysList
        .map(cacheKeyString =>
          (cacheKeyString, Integer.parseInt(cacheKeyString.split(","){4}))
        )

      cacheKeyAndTakeTuples.filter(_._2 < take)
        .foreach[Unit](tuplet => {
          client.hdel("cache-ages", tuplet._1)
          client.del(tuplet._1)
        })

      val largestCacheKeyAndTakeTuple = cacheKeyAndTakeTuples
        .sortBy(_._2)
        .reverse
        .lift(0)
      largestCacheKeyAndTakeTuple match {
        case Some(tuplet) =>
          if (tuplet._2 >= take) Unit
          else insertResultToCache.apply()
        case None =>
          insertResultToCache.apply()
      }

    })

  }

  def pipeline
  (
    requestPacket: NKeywordRecommendRequest[_, _],
    queryExecution: (() => KeywordRecommendResponse)
  ): (KeywordRecommendResponse, Map[String, String]) = {

    val keyword = requestPacket.keyword
    val ranker = requestPacket.ranker.get
    val take = requestPacket.take
    val vertexFinder = requestPacket.vertexFinder.get

    val (cachedResults, cacheAge) = {
      var cachedResultOption: Option[List[Option[String]]] = None
      var cachedResultAgeOption: Option[Long] = None

      redisPoolOption.get.withClient(client => {
        val cacheKeyOption = {
          val scanResults = client.scan(
            0,
            s"krr,${keyword.sorted.mkString(".")}," +
              s"${ranker.getClass.getName},${vertexFinder.getClass.getName}*",
            count = take
          )
          val (_, matchedKeys) = scanResults.getOrElse((None, None))

          matchedKeys
            .getOrElse(List())
            .flatten
            .filter(keyname =>
              Integer.parseInt(keyname.split(","){4}) >= take
            )
            .lift(0)
        }
        cachedResultOption = cacheKeyOption
          .flatMap(cacheKeyString => client.lrange(cacheKeyString, 0, take))
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
      queryExecutionResponse.result
        .foreach(results => cacheKeywordRecommendationInRedis(
          keyword, ranker, vertexFinder, take, results
        ))
      (queryExecutionResponse, Map("cache-age" -> cacheAge.toString))
    } else (
      KeywordRecommendResponse(
        isSuccessful = true,
        message = Some("Success"),
        result = Some(cachedResults)
      ), Map("cache-age" -> cacheAge.toString))
  }

  def unconditionalPipeline
  (
    requestPacket: NKeywordRecommendRequest[_, _],
    queryExecution: (() => KeywordRecommendResponse)
  ): KeywordRecommendResponse = {
    val keyword = requestPacket.keyword
    val ranker = requestPacket.ranker.get
    val take = requestPacket.take
    val vertexFinder = requestPacket.vertexFinder.get

    val keywordRecommendResponse = queryExecution.apply()
    keywordRecommendResponse.result match {
      case Some(result) => cacheKeywordRecommendationInRedis(
        keyword, ranker, vertexFinder, take, result
      )
      case None => Unit
    }

    keywordRecommendResponse
  }

  def close() = redisPoolOption.foreach(_.close)

}
