package com.wavesplatform.txgenerator

import scorex.crypto.hash.Blake2b256

object SeedGenerator {

  def generateSeed(textA: String, textB: String): Array[Byte] = {
    val hashA = Blake2b256.hash(textA)
    val hashB = Blake2b256.hash(textB)

    hashA ++ hashB
  }
}
