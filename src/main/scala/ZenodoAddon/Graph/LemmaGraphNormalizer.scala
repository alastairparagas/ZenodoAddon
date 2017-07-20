package ZenodoAddon.Graph

import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

import edu.stanford.nlp.ling.CoreAnnotations.{
  LemmaAnnotation,
  PartOfSpeechAnnotation,
  TokensAnnotation
}
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import oracle.pgx.api.{EdgeSet, GraphBuilder, PgxEdge, PgxGraph}
import oracle.pgx.api.filter.EdgeFilter
import oracle.pgx.common.types.IdType

import scala.collection.JavaConverters
import scala.util.control.Exception


class LemmaGraphNormalizer extends GraphNormalizer[PgxGraph]
{

  /**
    * Construct a new graph by normalizing keywords based on noun POS and then
    * doing lemmatization
    * @param graph: PgxGraph
    * @return PgxGraph
    */
  def normalize(graph: PgxGraph): PgxGraph = {

    def normalizeToWords(textCorpus: String): Iterator[String] = {
      val annotation = new Annotation(textCorpus)
      val properties = new Properties()
      properties.setProperty("annotators", "tokenize, pos, lemma")
      properties.setProperty("tokenize.language", "en")
      new StanfordCoreNLP(properties).annotate(annotation)

      val tokensList: Iterator[CoreLabel] =
        JavaConverters
          .asScalaBuffer(annotation.get(classOf[TokensAnnotation]))
          .toIterator
      for {
        token <- tokensList
        pos: String = token.get(classOf[PartOfSpeechAnnotation])
        lemma: String = token.get(classOf[LemmaAnnotation])

        if List("NN", "NNS", "NNP", "NNPS").contains(pos)
      } yield lemma
    }

    val edgeId = new AtomicInteger(1)
    val graphBuilder: GraphBuilder[String] =
      graph.getSession.newGraphBuilder(IdType.STRING)

    val docToKeywordEdges: Iterator[PgxEdge] = JavaConverters.asScalaIterator(
      (graph.getEdges(
        new EdgeFilter("src.type == \"document\" && dest.type == \"keyword\"")
      ): EdgeSet).iterator()
    )

    docToKeywordEdges
      .toStream
      .par
      .foreach((docToKeywordEdge: PgxEdge) => {
        val documentId: String = docToKeywordEdge.getSource.getId
        val keyword: String = docToKeywordEdge.getDestination.getId

        normalizeToWords(keyword)
          .foreach((normalizedNoun: String) => {
            graphBuilder.addVertex(documentId)
            Exception.ignoring(classOf[RuntimeException]) {
              graphBuilder.addVertex(normalizedNoun)
            }
            graphBuilder.addEdge(
              edgeId.getAndIncrement(),
              documentId,
              normalizedNoun
            )
          })
      })

    graphBuilder.build()
  }

}
