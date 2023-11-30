package ECPT.TestRegion
import org.scalatest.funsuite.AnyFunSuite
import chisel3._
import chisel3.util._
import scala.util.Random


class TestRegion extends AnyFunSuite {
    def getBitGroups(value: UInt): Array[Int] = {
        val bits27 = value(26, 0)  // Ensure the UInt is 27 bits wide
        val groups = new Array[Int](8)

        // Extracting 5 groups of 3 bits from LSB
        for (i <- 0 until 5) {
            groups(i) = bits27(3 * i + 2, 3 * i).litValue.toInt
        }

        // Extracting 3 groups of 4 bits from MSB
        for (i <- 0 until 3) {
            groups(i + 5) = bits27(26 - 4 * i, 23 - 4 * i).litValue.toInt
        }

        groups
    }


    def generateRandomBinaryString(): String = {
        // Function to generate a random binary string of a given length
        def randomBinaryGroup(length: Int): String = {
            Seq.fill(length)(Random.nextInt(2)).mkString
        }

        // Generating random groups
        val lsbGroups = (1 to 5).map(_ => randomBinaryGroup(3)).mkString("_")
        val msbGroups = (1 to 3).map(_ => randomBinaryGroup(4)).mkString("_")

        // Concatenating all parts
        "b" + msbGroups + "_" + lsbGroups
    }
  
    test("Example Test 1") {
        // assert(1 + 1 == 2)
        val result = getBitGroups("b1110_1100_1010_000_000_111_010_101".U)
        for (i <- 0 until 8) {
            println(s"${result(i).toBinaryString}")
        }
        println("TEST REGION DONE")
    }

    test("Example Test 2") {
        // assert("Hello".toLowerCase == "hello")
        for (i <- 0 until 10){
            println(generateRandomBinaryString())
        }
        val test = generateRandomBinaryString()
        println(s"${getBitGroups(test.U)(0)}")
    }
  
}

