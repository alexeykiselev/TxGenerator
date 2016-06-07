package com.wavesplatform.txgenerator

import java.io.{File, FileReader}

import com.google.common.primitives.{Bytes, Ints}
import com.opencsv.CSVReader
import scopt.OptionParser
import scorex.account.{Account, PrivateKeyAccount}
import scorex.crypto.encode.Base58
import scorex.crypto.hash.SecureCryptographicHash

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.io.StdIn

sealed trait Mode

case object Seeding extends Mode

case object Generating extends Mode

case object Sending extends Mode

case class TxGeneratorConfiguration(
                                     mode: Mode = Seeding,
                                     transactions: File = new File("transaction.csv"),
                                     host: String = "localhost",
                                     port: Int = 6969,
                                     https: Boolean = false,
                                     limit: Int = 100)

object TxGenerator extends App {
  override def main(args: Array[String]) {

    val parser = new OptionParser[TxGeneratorConfiguration]("txgenerator") {
      head("TxGenerator - Waves transactions generator", "v1.0.0")
      cmd("seed") action { (_, c) =>
        c.copy(mode = Seeding)
      } text "Generate seed"
      cmd("generate") action { (_, c) =>
        c.copy(mode = Generating)
      } text "Generate transactions" children (
        opt[File]('f', "file") required() valueName "<file>" action { (x, c) =>
          c.copy(transactions = x)
        } validate { x =>
          if (x.exists()) success else failure(s"Failed to open file $x")
        } text "path to transactions file")
      cmd("send") action { (_, c) =>
        c.copy(mode = Sending)
      } text "Send transactions to server" children(
        opt[String]('h', "host") required() valueName "<host>" action { (x, c) =>
          c.copy(host = x)
        } text "node host name or IP address",
        opt[Int]('p', "port") valueName "<port>" action { (x, c) => c.copy(port = x) } text "node port number",
        opt[Int]('l', "limit") valueName "<limit>" action { (x, c) => c.copy(limit = x) } text "batch transactions limit",
        opt[Unit]('s', "https") action { (_, c) => c.copy(https = true) } text "use HTTPS connection")
      help("help") text "display this help message"
    }

    parser.parse(args, TxGeneratorConfiguration()) match {
      case Some(config) => {
        config.mode match {
          case Seeding =>
            println("Please, enter the first text (Empty line to finish):")
            val textA = readConsole(Vector[String]())

            println("Enter the second text (Empty line to finish):")
            val textB = readConsole(Vector[String]())

            val seed = SeedGenerator.generateSeed(textA.mkString("\n"), textB.mkString("\n"))
            val seedString = Base58.encode(seed)
            println(s"Seed: $seedString")

            val accountSeed = SecureCryptographicHash(Bytes.concat(Ints.toByteArray(0), seed))
            val accountSeedString = Base58.encode(accountSeed)
            println(s"Account seed: $accountSeedString")

            val account = new PrivateKeyAccount(accountSeed)
            println(s"Address: ${account.address}")

          case Generating =>
            println(s"Processing file: ${config.transactions}")

            val reader = new CSVReader(new FileReader(config.transactions))
            for (row <- reader.readAll) {
              val address = row(0).trim
              val amount = row(1).trim.toLong

              createTransaction(address, amount)
            }
          case Sending =>
        }
      }
      case None =>
    }
  }

  @tailrec
  def readConsole(strings: Vector[String]): Vector[String] = {
    val string = StdIn.readLine

    if (string.isEmpty) strings else readConsole(strings.:+(string))
  }

  def createTransaction(address: String, amount: Long) = {
    println(s"Creating transaction to address '$address' with amount ~$amount")
    getAccount(address) match {
      case Some(account) =>
        println(account)
      //val transaction = new PaymentTransaction( account, amount, 1, )
      case None =>
        println(s"Skipping invalid address '$address'")
    }
    //    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    //      Http().outgoingConnection()
    //
    //    HttpRequest(uri = "/").withMethod(HttpMethods.POST)
    //
    //
    //  for (group <- reader.readAll grouped config.limit) {
  }

  def getAccount(address: String): Option[Account] = {
    if (address.matches("^[w1|w0](.+)$") && Account.isValidAddress(address)) Some(new Account(address)) else None
  }
}
