package com.wavesplatform.txgenerator

import java.io.{File, FileReader}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.opencsv.CSVReader

import scala.collection.JavaConversions._
import scopt.OptionParser
import scorex.transaction.PaymentTransaction

case class TxGeneratorConfiguration(transactions: File = new File("transaction.csv"),
                                    host: String = "localhost",
                                    port: Int = 6969,
                                    https: Boolean = false,
                                    limit: Int = 100)

object TxGenerator {
  def main(args: Array[String]) {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val parser = new OptionParser[TxGeneratorConfiguration]("txgenerator") {
      head("TxGenerator - Waves transactions generator", "v1.0.0")
      opt[File]('f', "file") required() valueName "<file>" action { (x, c) =>
        c.copy(transactions = x)
      } validate { x =>
        if (x.exists()) success else failure(s"Failed to open file $x")
      } text "path to transactions file"
      opt[String]('h', "host") required() valueName "<host>" action { (x, c) =>
        c.copy(host = x)
      } text "node host name or IP address"
      opt[Int]('p', "port") valueName "<port>" action { (x, c) => c.copy(port = x) } text "node port number"
      opt[Int]('l', "limit") valueName "<limit>" action { (x, c) => c.copy(limit = x)} text "batch transactions limit"
      opt[Unit]('s', "https") action { (_, c) => c.copy(https = true) } text "use HTTPS connection"
      help("help") text "display this help message"
    }

    parser.parse(args, TxGeneratorConfiguration()) match {
      case Some(config) => {
        println(s"Processing file: ${config.transactions}")
        println()

        val reader = new CSVReader(new FileReader(config.transactions))

        for (group <- reader.readAll grouped config.limit) {
          for (row <- group) {
            val address = row(0).trim
            val amount = row(1).trim.toLong

            createTransaction(address, amount)
          }
          println("Checking...")
        }
      }
      case None =>
    }

  }

  def createTransaction(address: String, amount: Long) = {
    println(s"  Sending ${amount} wavelets to ${address}")
    val transaction = PaymentTransaction()
    //    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
    //      Http().outgoingConnection()
    //
    //    HttpRequest(uri = "/").withMethod(HttpMethods.POST)
    //
    //
  }
}
