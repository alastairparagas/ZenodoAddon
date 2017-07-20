name := "ZenodoAddon"
version := "0.1.0"
scalaVersion := "2.12.2"
mainClass := Some("ZenodoAnomalyDetection.Main")
unmanagedBase := baseDirectory.value / "lib"
libraryDependencies ++= Seq(
  "edu.stanford.nlp" % "stanford-corenlp" % "3.8.0",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.8.0" classifier "models",
  "org.rogach" %% "scallop" % "3.0.3",
  "com.sparkjava" % "spark-core" % "2.6.0",
  "io.argonaut" %% "argonaut" % "6.2"
)
