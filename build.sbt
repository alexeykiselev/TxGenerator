import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._

val commonSettings = Seq(
  name := "TxGenerator",
  version := "1.0.0",
  scalaVersion := "2.11.8",
  resolvers += "SonaType" at "https://oss.sonatype.org/content/groups/public",
  mainClass in assembly := Some("com.wavesplatform.txgenerator.TxGenerator"),
  assemblyJarName in assembly := "tx-gen-test.jar",
  assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
    case "application.conf" => MergeStrategy.concat
    case "logback.xml" => MergeStrategy.first
    case x => old(x)
  }
  }
)

val dependencies = Seq(
  "com.opencsv" % "opencsv" % "3.7",
  "com.github.scopt" %% "scopt" % "3.6.0",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "org.consensusresearch" % "scrypto_2.11" % "1.0.+",
  "org.consensusresearch" %% "scorex-transaction" % "1.2.8"
)

lazy val root = (project in file("."))
  //  .configs(ProfileMainNet, ProfileTestNet)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= dependencies
  )
