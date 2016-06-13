import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._

lazy val commonSettings = Seq(
  name := "TxGenerator",
  version := "1.0.0",
  scalaVersion := "2.11.8",
  resolvers += "SonaType" at "https://oss.sonatype.org/content/groups/public",
  assemblyJarName in assembly := "tx-gen.jar",
  mainClass in assembly := Some("com.wavesplatform.txgenerator.TxGenerator"),
  assemblyMergeStrategy in assembly := {
    case "application.conf" => MergeStrategy.concat
    case "logback.xml" => MergeStrategy.first
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

val dependencies = Seq(
  "com.opencsv" % "opencsv" % "3.7",
  "com.github.scopt" %% "scopt" % "3.4.+",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "org.consensusresearch" % "scrypto_2.11" % "1.0.+",
  "org.consensusresearch" %% "scorex-transaction" % "1.2.8-SNAPSHOT"
)

//lazy val ProfileTestNet = config("testnet") extend (Compile)
//lazy val ProfileMainNet = config("mainnet") extend (Compile)

lazy val root = (project in file("."))
//  .configs(ProfileMainNet, ProfileTestNet)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= dependencies
  )
//  .settings(
//    managedResourceDirectories in ProfileTestNet := Seq(baseDirectory.value / "src/main/testnet/resources"),
//
//  )
//  .settings(
//    managedResourceDirectories in ProfileMainNet := Seq(baseDirectory.value / "src/main/mainnet/resources"),
//
//    assemblyMergeStrategy in assembly := {
//      case "application.conf" => MergeStrategy.concat
//      case "logback.xml" => MergeStrategy.first
//      case x =>
//        val oldStrategy = (assemblyMergeStrategy in assembly).value
//        oldStrategy(x)
//    }
//  )