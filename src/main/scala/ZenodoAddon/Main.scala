package ZenodoAddon

import Graph.{
  KeywordRecommendRequest,
  KeywordRecommendResponse,
  Runner => GraphRunner
}

import argonaut._
import argonaut.Argonaut._
import spark.{Request, Response}
import spark.Spark.{initExceptionHandler, post}

object Main extends App
{

  new StartupArgs()
    .run(args)
    .map(startupArgs => {
      println("Starting up GraphRunner")
      val graphRunner = new GraphRunner()
      graphRunner
        .startup(
          graphEngineDsn = startupArgs.graphEngineDsn,
          graphSettingsFilePath = startupArgs.graphSettingsFile
        )
        .get
      this.setupServer(graphRunner)
      graphRunner.shutdown().get
    })
    .recover({
      case error: Throwable => println("Error: " + error.getMessage)
    })
    .get

  private def setupServer(graphRunner: GraphRunner): Unit = {

    implicit def KeywordRecommendResponseEncode:
    EncodeJson[KeywordRecommendResponse] =
      EncodeJson((k: KeywordRecommendResponse) => {
        ("data" := k.result.getOrElse(List())) ->:
          ("message" := k.message.getOrElse("")) ->:
          ("success" := k.isSuccessful) ->: jEmptyObject
      })

    implicit def KeywordRecommendRequestDecode:
    DecodeJson[KeywordRecommendRequest] =
      DecodeJson((c: HCursor) => {
        for {
          ranker <- (c --\ "ranker").as[Option[String]]
          keyword <- (c --\ "keyword").as[Option[String]]
          normalizer <- (c --\ "normalizer").as[Option[String]]
        } yield KeywordRecommendRequest(
          normalizer=normalizer,
          ranker=ranker.get,
          keyword=keyword.get
        )
      })

    def recommendationHandler(request: Request,
                              response: Response) : KeywordRecommendResponse = {
      if (request.contentType() != "application/json")
        KeywordRecommendResponse(
          isSuccessful = false,
          message = Some("Payload must be JSON"),
          result = None
        )

      val keywordRecommendRequestOpt: Option[KeywordRecommendRequest] =
        Parse.decodeOption[KeywordRecommendRequest](request.body)
      if (keywordRecommendRequestOpt.isEmpty) {
        KeywordRecommendResponse(
          isSuccessful = false,
          message = Some("Invalid JSON payload"),
          result = None
        )
      }

      graphRunner
        .query(keywordRecommendRequestOpt.get)
        .recover({
          case error: Throwable => KeywordRecommendResponse(
            isSuccessful = false,
            message = Some(error.getMessage),
            result = None
          )
        })
        .get
    }

    post("/recommendation", (req, res) =>
      KeywordRecommendResponseEncode.encode(
        recommendationHandler(req, res)
      )
    )
    initExceptionHandler(e => throw e)
  }

}
