name := "TxGenerator"

version := "1.0.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.opencsv" % "opencsv" % "3.7",
  "com.github.scopt" %% "scopt" % "3.4.+",
  "com.typesafe.akka" %% "akka-http-core" % "2.4.6",
  "org.consensusresearch" %% "scorex-transaction" % "1.2.7")