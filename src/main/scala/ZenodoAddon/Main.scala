package ZenodoAddon

import ZenodoAddon.EnvironmentArgs.{EnvironmentArgsRecord, Runner}
import ZenodoAddon.Graph.{KeywordRecommendRequest, KeywordRecommendResponse}
import ZenodoAddon.Graph.Pgx.PgxRunner

import scala.util.Try
import argonaut._
import argonaut.Argonaut._
import spark.{Request, Response}
import spark.Spark.{port, post, stop}


object Main extends App
{

  new Runner()
    .run(args)
    .map(environmentArgs => {
      port(environmentArgs.port)
      setupServer(environmentArgs)
    })
    .recover({
      case throwable: Throwable => {
        println("Error: " + throwable.getMessage)
        stop()
        System.exit(0)
      }
    })

  private def setupServer(environmentArgs: EnvironmentArgsRecord): Unit = {

    implicit def KeywordRecommendResponseEncode:
    EncodeJson[KeywordRecommendResponse] =
      EncodeJson((k: KeywordRecommendResponse) => {
        ("data" := (
          ("results" := k.result.getOrElse(List())) ->:
            ("addons" := k.addonsMetadata) ->: jEmptyObject
        )) ->:
          ("message" := k.message.getOrElse("")) ->:
          ("success" := k.isSuccessful) ->: jEmptyObject
      })

    implicit def KeywordRecommendRequestDecode:
    DecodeJson[KeywordRecommendRequest] =
      DecodeJson((c: HCursor) => {
        for {
          ranker <- (c --\ "ranker").as[String]
          vertexFinder <- (c --\ "vertexFinder").as[String]
          keyword <- (c --\ "keyword").as[List[String]]
          normalizer <- (c --\ "normalizer").as[Option[String]]
          count <- (c --\ "count").as[Option[Int]]
          addons <- (c --\ "addons").as[Option[List[String]]]
        } yield KeywordRecommendRequest(
          normalizer=normalizer,
          ranker=ranker,
          vertexFinder=vertexFinder,
          addons=addons.getOrElse(List()),
          keyword=keyword,
          take=count.getOrElse(5)
        )
      })

    val graphRunner = new PgxRunner()
    graphRunner.startup(environmentArgs)

    def recommendationHandler(request: Request,
                              response: Response) : KeywordRecommendResponse = {
      response.`type`("application/json")

      if (request.contentType() != "application/json") {
        KeywordRecommendResponse(
          isSuccessful = false,
          message = Some("Payload must be JSON"),
          result = None
        )
      } else {
        val jsonBody = request.body

        val keywordRecommendRequestOption: Option[KeywordRecommendRequest] =
          Parse.decodeOption[KeywordRecommendRequest](jsonBody)

        if (keywordRecommendRequestOption.isEmpty) {
          KeywordRecommendResponse(
            isSuccessful = false,
            message = Some("Invalid JSON payload"),
            result = None
          )
        } else {
          Try({keywordRecommendRequestOption.get})
            .flatMap(graphRunner.query)
            .recover({
              case error: Throwable => {
                val errorString = error.getMessage
                if (errorString == null) {
                  KeywordRecommendResponse(
                    isSuccessful = false,
                    message = Some(error.toString),
                    result = None
                  )
                } else {
                  KeywordRecommendResponse(
                    isSuccessful = false,
                    message = Some(errorString),
                    result = None
                  )
                }
              }
            })
            .get
        }
      }
    }

    post("/recommendation", (req, res) => KeywordRecommendResponseEncode.encode(
      recommendationHandler(req, res)
    ))
  }

}
