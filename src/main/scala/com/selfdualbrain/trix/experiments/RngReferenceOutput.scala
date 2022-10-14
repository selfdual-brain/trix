package com.selfdualbrain.trix.experiments

import org.apache.commons.math3.random.MersenneTwister

import java.io.{BufferedWriter, File, FileWriter}
import scala.util.Using

object RngReferenceOutput {

  val outputDirPath = "c:\\Users\\wojci\\tmp\\mersenne-twister\\"
  val outputDir = new File(outputDirPath)

  def main(args: Array[String]): Unit = {
    generateReferenceSequence(42)
    generateReferenceSequence(100)
    generateReferenceSequence(479001600)
  }

  def generateReferenceSequence(seed: Int): Unit = {
    val rng = new MersenneTwister(seed)
    val outputFile = new File(outputDir, s"seed-$seed.txt")

    Using (new BufferedWriter(new FileWriter(outputFile))) { writer =>
      for (i <- 1 to 10000) {
        val nextRandomLongValue: Long = rng.nextLong()
        writer.write(nextRandomLongValue.toString)
        writer.write("\n")
      }
    }
  }

}
