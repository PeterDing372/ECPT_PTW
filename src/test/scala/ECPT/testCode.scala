package ECPT.TestRegion
import org.scalatest.funsuite.AnyFunSuite
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import freechips.rocketchip.rocket._
import chipsalliance.rocketchip.config._
import ECPT_Test._
import ECPT.Params._
import ECPT.Debug._

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
  
    test("Example Test 1") {
        // assert(1 + 1 == 2)
        val result = getBitGroups("b1111_1110_1100_1010_000_000_111_010_101".U)
        for (i <- 0 until 8) {
            println(s"${result(i).toBinaryString}")
        }
        println("TEST REGION DONE")
    }

    // test("Example Test 2") {
    //     assert("Hello".toLowerCase == "hello")
    // }
  
  // Add more tests as needed
}

