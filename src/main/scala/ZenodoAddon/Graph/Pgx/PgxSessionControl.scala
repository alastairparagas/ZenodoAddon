package ZenodoAddon.Graph.Pgx

import ZenodoAddon.Graph.{GraphNormalizer, SessionControl}
import oracle.pgx.api.{Pgx, PgxGraph, PgxSession}
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock


/**
  * A thread-safe wrapper around a graph engine session where we can
  * transform a loaded graph or obtain a copy of it
  */
class PgxSessionControl extends SessionControl[PgxSession, PgxGraph]
{

  private val sessionIsRunning: AtomicBoolean =
    new AtomicBoolean(false)
  private var session: Option[PgxSession] = None
  private var sessionName: Option[String] = None
  private var mainGraph: Option[PgxGraph] = None
  private val mainGraphLock: ReentrantReadWriteLock =
    new ReentrantReadWriteLock()

  private def checkSessionIsRunning(): Unit = {
    if (!sessionIsRunning.get) throw new RuntimeException(
      "Session isn't initialized"
    )
  }

  private def checkGraphIsLoaded(): Unit = {
    if (mainGraph.isEmpty) throw new RuntimeException(
      "Graph isn't loaded"
    )
  }

  /**
    * Initialize a session with the Graph Engine Server
    * @param dsn: String
    * @return
    */
  def initializeSession(dsn: String): PgxSession = {
    if (!sessionIsRunning.compareAndSet(false, true)) {
      throw new RuntimeException(
        "Session already initialized - cannot re-initialize again!"
      )
    }

    sessionName = Some(Instant.now.toString)
    val pgxSession = Pgx
      .getInstance(dsn)
      .createSession(sessionName.get)
    session = Some(pgxSession)

    pgxSession
  }

  /**
    * Load graph configuration settings on the Graph Engine Server
    * @param settingsFilePath: String
    * @return
    */
  def loadSettings(settingsFilePath: String): Unit = {
    checkSessionIsRunning()

    mainGraphLock.writeLock().lock()
    try {
      mainGraph = Some(
        session.get.readGraphWithProperties(settingsFilePath)
      )
    } finally {
      mainGraphLock.writeLock().unlock()
    }
  }

  /**
    * Get a copy of the graph
    * @return PgxGraph
    */
  def getGraph: PgxGraph = {
    mainGraphLock.readLock().lock()

    checkSessionIsRunning()
    checkGraphIsLoaded()

    try {
      checkSessionIsRunning()

      mainGraph.get.clone()
    } finally {
      mainGraphLock.readLock().unlock()
    }
  }

  /**
    * Transform the graph into a new graph
    * @param transformer: PgxGraph => PgxGraph
    */
  def transformGraph(transformer: PgxGraph => PgxGraph): Unit = {
    mainGraphLock.writeLock().lock()

    checkSessionIsRunning()
    checkGraphIsLoaded()

    try {
      val originalGraph = mainGraph.get
      val transformedGraph = transformer(originalGraph)

      mainGraph = Some(transformedGraph)
      originalGraph.destroy()
    } finally {
      mainGraphLock.writeLock().unlock()
    }
  }

  def transformGraph(normalizer: GraphNormalizer[PgxGraph]): Unit =
    transformGraph(normalizer.normalize _)

  /**
    * Destroy the session with the Graph Engine Server
    * @return
    */
  def destroySession(): Unit = {
    if (sessionIsRunning.compareAndSet(true, false) && mainGraph.isDefined) {
      mainGraphLock.writeLock().lock()
      try {
        session.get.close()
        mainGraph.get.destroy()
      } finally {
        mainGraphLock.writeLock().unlock()
      }
    }
  }

}
