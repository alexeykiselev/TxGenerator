name := "TxGenerator"

version := "1.0.0"

scalaVersion := "2.11.8"

resolvers += "SonaType" at "https://oss.sonatype.org/content/groups/public"

libraryDependencies ++= Seq(
  "com.opencsv" % "opencsv" % "3.7",
  "com.github.scopt" %% "scopt" % "3.4.+",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "org.consensusresearch" % "scrypto_2.11" % "1.0.+",
  "org.consensusresearch" %% "scorex-transaction" % "1.2.8-SNAPSHOT")

assemblyJarName in assembly := "tx-gen.jar"
mainClass in assembly := Some("com.wavesplatform.txgenerator.TxGenerator")

assemblyMergeStrategy in assembly := {
  case "application.conf" => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}