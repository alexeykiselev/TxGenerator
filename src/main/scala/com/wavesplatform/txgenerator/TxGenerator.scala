package com.wavesplatform.txgenerator

import java.io.{File, FileReader, FileWriter}

import com.google.common.primitives.{Bytes, Ints}
import com.opencsv.{CSVReader, CSVWriter}
import dispatch.Defaults._
import dispatch._
import org.joda.time.DateTime
import org.joda.time.{Duration => JodaDuration}
import play.api.libs.json._
import scopt.OptionParser
import scorex.account.{Account, PrivateKeyAccount}
import scorex.crypto.encode.Base58
import scorex.transaction.PaymentTransaction

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

sealed trait Mode

case object SeedGeneration extends Mode

case object TransactionGeneration extends Mode

case object TransactionSending extends Mode

case object Checking extends Mode

case class TxGeneratorConfiguration(mode: Mode = SeedGeneration,
                                    transactions: File = new File("transaction.csv"),
                                    output: File = new File("output.csv"),
                                    seed: Array[Byte] = Array[Byte](),
                                    accountSeed: Array[Byte] = Array[Byte](),
                                    privateKey: Array[Byte] = Array[Byte](),
                                    publicKey: Array[Byte] = Array[Byte](),
                                    offset: Long = 0,
                                    host: String = "localhost",
                                    port: Int = 6869,
                                    https: Boolean = false,
                                    limit: Int = 100,
                                    delay: Int = 15000,
                                    iterations: Int = 12,
                                    nonce: Int = 0)

object TxGenerator extends App {

  override def main(args: Array[String]) {

    val parser = new OptionParser[TxGeneratorConfiguration]("txgenerator") {
      head("TxGenerator - Waves transactions generator", "v1.0.0")
      cmd("seed") action { (_, c) =>
        c.copy(mode = SeedGeneration)
      } text "Generate seed" children(
        opt[Int]('n', "nonce") valueName "<nonce>" action { (x, c) => c.copy(nonce = x) } text "address generation nonce",
        opt[String]('s', "seed") valueName "<seed>" action { (x, c) =>
          c.copy(seed = if (Base58.decode(x).isSuccess) Base58.decode(x).get else Array[Byte]())
        } validate { x =>
          if (!x.isEmpty) success else failure("Invalid Base58 string for sender account seed")
        } text "previously generated seed")
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
        opt[String]('s', "acc-seed") required() valueName "<account-seed>" action { (x, c) =>
          c.copy(accountSeed = if (Base58.decode(x).isSuccess) Base58.decode(x).get else Array[Byte]())
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
          c.copy(offset = x)
        } text "offset to timestamp")
      cmd("send") action { (_, c) =>
        c.copy(mode = TransactionSending)
      } text "Send transactions to server" children(
        opt[File]('f', "file") required() valueName "<file>" action { (x, c) =>
          c.copy(transactions = x)
        } validate { x =>
          if (x.exists()) success else failure(s"Failed to open file $x")
        } text "path to transactions file",
        opt[String]('h', "host") required() valueName "<host>" action { (x, c) =>
          c.copy(host = x)
        } text "node host name or IP address",
        opt[Int]('p', "port") valueName "<port>" action { (x, c) => c.copy(port = x) } text "node port number",
        opt[Int]('l', "limit") valueName "<limit>" action { (x, c) => c.copy(limit = x) } text "batch transactions limit",
        opt[Unit]('s', "https") action { (_, c) => c.copy(https = true) } text "use HTTPS connection",
        opt[String]('u', "public") required() valueName "<sender-public-key>" action { (x, c) =>
          c.copy(publicKey = if (Base58.decode(x).isSuccess) Base58.decode(x).get else Array[Byte]())
        } validate { x =>
          if (!x.isEmpty) success else failure("Invalid Base58 string for sender public key")
        } text "sender public key as Base58 string",
        opt[Int]('d', "delay") valueName "<delay>" action { (x, c) => c.copy(delay = x) } text "delay in ms between transactions checks",
        opt[Int]('i', "iterations") valueName "<iterations>" action { (x, c) => c.copy(iterations = x) } text "transactions checks count")
      cmd("check") action { (_, c) =>
        c.copy(mode = Checking)
      } text "Check transactions on server" children(
        opt[File]('f', "file") required() valueName "<file>" action { (x, c) =>
          c.copy(transactions = x)
        } validate { x =>
          if (x.exists()) success else failure(s"Failed to open file $x")
        } text "path to transactions file",
        opt[File]('o', "output") required() valueName "<output-file>" action { (x, c) =>
          c.copy(output = x)
        } text "path to report file",
        opt[String]('h', "host") required() valueName "<host>" action { (x, c) =>
          c.copy(host = x)
        } text "node host name or IP address",
        opt[Int]('p', "port") valueName "<port>" action { (x, c) => c.copy(port = x) } text "node port number",
        opt[Int]('l', "limit") valueName "<limit>" action { (x, c) => c.copy(limit = x) } text "batch transactions limit",
        opt[Unit]('s', "https") action { (_, c) => c.copy(https = true) } text "use HTTPS connection")
      help("help") text "display this help message"
    }

    parser.parse(args, TxGeneratorConfiguration()) match {
      case Some(config) =>
        config.mode match {
          case SeedGeneration =>
            val seed: Array[Byte] = if (config.seed.isEmpty) {
              println("Please, enter the first text (Empty line to finish):")
              val textA = readConsole(Vector[String]())

              println("Enter the second text (Empty line to finish):")
              val textB = readConsole(Vector[String]())

              SeedGenerator.generateSeed(textA.mkString("\n"), textB.mkString("\n"))
            } else config.seed

            val seedString = Base58.encode(seed)
            println(s"Seed: $seedString")

            val saltedSeed = Bytes.concat(Ints.toByteArray(config.nonce), seed)
            val accountSeed = HashChain.hash(saltedSeed)
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

            val failed = mutable.Buffer[Array[String]]()

            config.output.createNewFile()
            val writer = new CSVWriter(new FileWriter(config.output))
            var created = 0

            for (row <- reader.readAll if row.size == 2) {
              val address = row(0).trim
              val amount = row(1).trim.toLong

              createTransaction(config.accountSeed, config.privateKey, config.publicKey, address, amount, config.offset) match {
                case Some(transaction) =>
                  created += 1
                  val time = new DateTime(transaction.timestamp).toDateTime.toString()
                  val line = Array[String](time, Base58.encode(transaction.signature), Base58.encode(transaction.bytes))
                  writer.writeNext(line)
                case None =>
                  failed += row
              }
            }
            writer.close()
            println(s"Transactions created: $created")
            val errors = failed.size
            if (failed.nonEmpty) {
              println(s"Invalid transactions: ${failed.size}")
              dumpFailedRows(failed, config.transactions.getName)
            }
            println(s"Records processed: ${created + errors}")
          case TransactionSending =>
            println(s"Processing file: ${config.transactions}")
            val reader = new CSVReader(new FileReader(config.transactions))

            val failed = mutable.Buffer[Array[String]]()
            val rejected = mutable.Buffer[String]()

            for (group <- reader.readAll grouped config.limit) {
              val t0 = System.currentTimeMillis
              val (failedRows, payments) = splitEitherSeq(convertToPayments(config, group))
              val postResults = postPayments(config, payments)

              val successfulReplies = extractValueSeq(collectResults(postResults))
              val signaturesToCheck = convertToSignatures(successfulReplies)

              val (acceptedSignatures, rejectedSignatures) = checkSignatures(config, config.iterations, signaturesToCheck)

              val spent = new JodaDuration(System.currentTimeMillis() - t0)
              println(s"Accepted ${acceptedSignatures.size} of ${group.size} in ${spent.getStandardSeconds} seconds")

              failed ++= failedRows
              rejected ++= rejectedSignatures
            }

            dumpFailedRows(failed, config.transactions.getName)
            dumpRejectedSignatures(rejected, config.transactions.getName)

            Http.shutdown()

          case Checking =>
            println(s"Processing file: ${config.transactions}")
            val reader = new CSVReader(new FileReader(config.transactions))

            val accepted = mutable.Buffer[Array[String]]()

            for (group <- reader.readAll grouped config.limit) {
              val signatures = extractValueSeq(readSignatures(group))

              val acceptedPayments = extractValueSeq(checkPaymentsOnServer(config, signatures))

              accepted ++= acceptedPayments
            }

            dumpReport(accepted, config.output)

            Http.shutdown()
        }
      case None =>
    }
  }

  def dumpFailedRows(rows: Seq[Array[String]], filename: String) = {
    if (rows.nonEmpty) {
      val failedRowsFile = new File(filename + ".failed")
      failedRowsFile.createNewFile()

      val writer = new CSVWriter(new FileWriter(failedRowsFile))
      writer.writeAll(rows)
      writer.close()
    }
  }

  def dumpRejectedSignatures(signatures: Seq[String], filename: String) = {
    if (signatures.nonEmpty) {
      val rejectedSignaturesFile = new File(filename + ".rejected")
      rejectedSignaturesFile.createNewFile()

      val writer = new CSVWriter(new FileWriter(rejectedSignaturesFile))
      val rows = signatures.map(signature => Array[String](signature))
      writer.writeAll(rows)
      writer.close()
    }
  }

  def dumpReport(rows: Seq[Array[String]], output: File) = {
    if (rows.nonEmpty) {
      output.createNewFile()

      val writer = new CSVWriter(new FileWriter(output))
      writer.writeAll(rows)
      writer.close()
    }
  }

  def splitEitherSeq[A, B](list: Seq[Either[A, B]]): (Seq[A], Seq[B]) = {
    val (lefts, rights) = list.partition(_.isLeft)

    (lefts.map(_.left.get), rights.map(_.right.get))
  }

  def extractValueSeq[A](seq: Seq[Option[A]]): Seq[A] = {
    val definedValues: Seq[Option[A]] = seq.filter(_.isDefined)

    definedValues.map(_.get)
  }

  def convertToPayments(config: TxGeneratorConfiguration, rows: Seq[Array[String]]): Seq[Either[Array[String], WavesPayment]] = {
    rows.map(row => convertRowToWavesPayment(config, row))
  }

  def convertRowToWavesPayment(config: TxGeneratorConfiguration, row: Array[String]): Either[Array[String], WavesPayment] = {
    val timeString = row(0)
    val signatureString = row(1)
    val transactionString = row(2)

    val timeResult = Try(DateTime.parse(timeString))
    val signatureResult = Base58.decode(signatureString)
    val transactionResult = Base58.decode(transactionString)

    if (timeResult.isSuccess && signatureResult.isSuccess && transactionResult.isSuccess) {
      val time = timeResult.get
      val transactionBytes = transactionResult.get

      val now = DateTime.now
      if (time.isBefore(now)) {
        val parseResult = PaymentTransaction.parseBytes(transactionBytes)
        if (parseResult.isSuccess) {
          val transaction = parseResult.get
          val senderPublicKeyBase58 = Base58.encode(config.publicKey)
          val signatureBase58 = Base58.encode(transaction.signature)
          val recipientBase58 = Base58.encode(transaction.recipient.bytes)
          Right(WavesPayment(transaction.timestamp, transaction.amount, transaction.fee, senderPublicKeyBase58, recipientBase58, signatureBase58))
        } else Left(row)
      } else Left(row)
    } else Left(row)
  }

  def postPayments(config: TxGeneratorConfiguration, payments: Seq[WavesPayment]): Seq[Future[Option[String]]] = {
    val request = getRequest(config.host, config.port, config.https)

    payments.map(payment => postPayment(request, payment))
  }

  def postPayment(req: Req, payment: WavesPayment): Future[Option[String]] = {
    val body = Json.toJson(payment).toString
    val request = req / "waves" / "external-payment" << body

    def post = request.POST
    def requestWithContentType = post.setContentType("application/json", "UTF-8")

    Http(requestWithContentType OK as.String).option
  }

  def collectResults(futures: Seq[Future[Option[String]]]): Seq[Option[String]] = {
    val result: Future[Seq[Option[String]]] = Future.sequence(futures)

    Await.result(result, Duration.Inf)
  }

  def convertToSignatures(replies: Seq[String]): Seq[String] = {
    replies.map(reply => extractSignature(reply)).filter(o => o.isDefined).map(o => o.get)
  }

  def checkSignaturesOnServer(config: TxGeneratorConfiguration, signatures: Seq[String]): Seq[Future[Option[String]]] = {
    val request = getRequest(config.host, config.port, config.https)

    signatures.map(signature => checkSignature(request, signature))
  }

  def checkSignature(req: Req, signature: String): Future[Option[String]] = {
    val timestampString = System.currentTimeMillis().toString
    val request = req / "transactions" / "info" / signature <<? Map("t" -> timestampString)

    Http(request OK as.String).option
  }

  def extractSignature(reply: String): Option[String] = {
    val json = Json.parse(reply)

    (json \ "signature").toOption match {
      case Some(value) =>
        Some(value.as[String])
      case None =>
        None
    }
  }

  def extractPayment(reply: String): Option[Array[String]] = {
    val json = Json.parse(reply)

    val signatureOption = (json \ "signature").toOption
    val recipientOption = (json \ "recipient").toOption
    val timestampOption = (json \ "timestamp").toOption
    val amountOption = (json \ "amount").toOption
    val heightOption = (json \ "height").toOption

    if (signatureOption.isDefined && recipientOption.isDefined && timestampOption.isDefined &&
      amountOption.isDefined && heightOption.isDefined) {
      val signature = signatureOption.get.as[String]
      val recipient = recipientOption.get.as[String]
      val timestamp = timestampOption.get.as[Long].toString
      val amount = amountOption.get.as[Long].toString
      val height = heightOption.get.as[Long].toString

      Some(Array(signature, s"1W$recipient", timestamp, amount, height))
    } else
      None
  }

  def readSignatures(rows: Seq[Array[String]]): Seq[Option[String]] = {
    rows.map(row => readSignature(row))
  }

  def readSignature(row: Array[String]): Option[String] = {
    if (row.size > 1)
      Some(row(1))
    else
      None
  }

  @tailrec
  def checkSignatures(config: TxGeneratorConfiguration, iteration: Int, signatures: Seq[String]): (Seq[String], Seq[String]) = {
    Thread.sleep(config.delay)

    val i = iteration - 1
    val results = checkSignaturesOnServer(config, signatures)
    val replies = extractValueSeq(collectResults(results))

    val acceptedSignatures = extractValueSeq(replies.map(reply => extractSignature(reply)))
    val rejectedSignatures = signatures.filterNot(acceptedSignatures contains)

    if (rejectedSignatures.isEmpty || i == 0) (acceptedSignatures, rejectedSignatures)
    else checkSignatures(config, i, rejectedSignatures)
  }

  def checkPaymentsOnServer(config: TxGeneratorConfiguration, signatures: Seq[String]): Seq[Option[Array[String]]] = {
    val replies = extractValueSeq(collectResults(checkSignaturesOnServer(config, signatures)))
    replies.map(r => extractPayment(r))
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

  def getRequest(h: String, port: Int, secured: Boolean): Req = {
    if (secured) host(h, port).secure else host(h, port)
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
