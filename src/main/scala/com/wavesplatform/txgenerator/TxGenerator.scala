package com.wavesplatform.txgenerator

import java.io.{File, FileReader, FileWriter}

import com.google.common.primitives.{Bytes, Ints}
import com.opencsv.{CSVReader, CSVWriter}
import org.joda.time._
import scopt.OptionParser
import scorex.account.{Account, PrivateKeyAccount}
import scorex.crypto.encode.Base58
import scorex.transaction.PaymentTransaction

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.io.StdIn

sealed trait Mode

case object SeedGeneration extends Mode

case object TransactionGeneration extends Mode

case object TransactionSending extends Mode

case class TxGeneratorConfiguration(mode: Mode = SeedGeneration,
                                    transactions: File = new File("transaction.csv"),
                                    output: File = new File("output.csv"),
                                    seed: Array[Byte] = Array[Byte](),
                                    privateKey: Array[Byte] = Array[Byte](),
                                    publicKey: Array[Byte] = Array[Byte](),
                                    offset: Long = 0,
                                    host: String = "localhost",
                                    port: Int = 6969,
                                    https: Boolean = false,
                                    limit: Int = 100,
                                    nonce: Int = 0)

object TxGenerator extends App {
  override def main(args: Array[String]) {

    val parser = new OptionParser[TxGeneratorConfiguration]("txgenerator") {
      head("TxGenerator - Waves transactions generator", "v1.0.0")
      cmd("seed") action { (_, c) =>
        c.copy(mode = SeedGeneration)
      } text "Generate seed" children (
        opt[Int]('n', "nonce") valueName "<nonce>" action { (x, c) => c.copy(nonce = x) } text "address generation nonce")
      cmd("generate") action { (_, c) =>
        c.copy(mode = TransactionGeneration)
      } text "Generate transactions" children(
        opt[File]('f', "file") required() valueName "<file>" action { (x, c) =>
          c.copy(transactions = x)
        } validate { x =>
          if (x.exists()) success else failure(s"Failed to open file $x")
        } text "path to transactions file",
        opt[File]('o', "output") required() valueName "<output-file>" action { (x, c) =>
          c.copy(output = x)
        } text "path to file to write transactions",
        opt[String]('s', "seed") required() valueName "<sender-seed>" action { (x, c) =>
          c.copy(seed = if (Base58.decode(x).isSuccess) Base58.decode(x).get else Array[Byte]())
        } validate { x =>
          if (!x.isEmpty) success else failure("Invalid Base58 string for sender account seed")
        } text "sender private account seed as Base58 string",
        opt[String]('p', "private") required() valueName "<sender-private-key>" action { (x, c) =>
          c.copy(privateKey = if (Base58.decode(x).isSuccess) Base58.decode(x).get else Array[Byte]())
        } validate { x =>
          if (!x.isEmpty) success else failure("Invalid Base58 string for sender private key")
        } text "sender private key as Base58 string",
        opt[String]('u', "public") required() valueName "<sender-public-key>" action { (x, c) =>
          c.copy(publicKey = if (Base58.decode(x).isSuccess) Base58.decode(x).get else Array[Byte]())
        } validate { x =>
          if (!x.isEmpty) success else failure("Invalid Base58 string for sender public key")
        } text "sender public key as Base58 string",
        opt[Long]('t', "offset") valueName "<timestamp-offset>" action { (x, c) =>
          c.copy(offset = x) } text "offset to timestamp")
      cmd("send") action { (_, c) =>
        c.copy(mode = TransactionSending)
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
          case SeedGeneration =>
            println("Please, enter the first text (Empty line to finish):")
            val textA = readConsole(Vector[String]())

            println("Enter the second text (Empty line to finish):")
            val textB = readConsole(Vector[String]())

            val seed = SeedGenerator.generateSeed(textA.mkString("\n"), textB.mkString("\n"))
            val seedString = Base58.encode(seed)
            println(s"Seed: $seedString")

            val accountSeed = HashChain.hash(Bytes.concat(Ints.toByteArray(config.nonce), seed))
            val accountSeedString = Base58.encode(accountSeed)
            println(s"Account seed: $accountSeedString")

            val account = new PrivateKeyAccount(accountSeed)
            println(s"Address: ${account.address}")

            val privateKey = Base58.encode(account.privateKey)
            val publicKey = Base58.encode(account.publicKey)
            println(s"Private Key: $privateKey")
            println(s"Public Key: $publicKey")

          case TransactionGeneration =>
            println(s"Processing file: ${config.transactions}")

            val reader = new CSVReader(new FileReader(config.transactions))

            config.output.createNewFile()
            val writer = new CSVWriter(new FileWriter(config.output))
            var created = 0
            var errors = 0

            for (row <- reader.readAll) {
              val address = row(0).trim
              val amount = row(1).trim.toLong

              createTransaction(config.seed, config.privateKey, config.publicKey, address, amount, config.offset) match {
                case Some(transaction) =>
                  created += 1
                  val time = new DateTime(transaction.timestamp).toDateTime.toString()
                  val line = Array[String](time, Base58.encode(transaction.signature), Base58.encode(transaction.bytes))
                  writer.writeNext(line)
                case None =>
                  errors += 1
              }
            }
            writer.close()
            println(s"Transactions created: $created")
            if (errors != 0) println(s"Invalid transactions: $errors")
            println(s"Records processed: ${created + errors}")
          case TransactionSending =>
          //  for (group <- reader.readAll grouped config.limit) {
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

  def createTransaction(seed: Array[Byte], privateKey: Array[Byte], publicKey: Array[Byte],
                        address: String, amount: Long, offset: Long): Option[PaymentTransaction] = {
    getAccount(address) match {
      case Some(account) =>
        val senderAccount = PrivateKeyAccount(seed, privateKey, publicKey)
        val fee = 1
        val timestamp = System.currentTimeMillis + offset
        val signature = PaymentTransaction.generateSignature(senderAccount, account, amount, fee, timestamp)

        Option(new PaymentTransaction(senderAccount, account, amount, fee, timestamp, signature))
      case None =>
        None
    }
  }

  def getAccount(addressCandidate: String): Option[Account] = {
    val addressPattern = "^1W(.+)$".r
    val addressOption = addressPattern findFirstMatchIn addressCandidate

    addressOption match {
      case Some(addressMatch) =>
        val address = addressMatch.group(1)
        if (Account.isValidAddress(address)) Some(new Account(address)) else None
      case None =>
        None
    }
  }
}
