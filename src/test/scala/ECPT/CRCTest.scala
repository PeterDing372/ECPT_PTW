package ECPT.TestRegion
import chisel3._
import chisel3.util._
import org.scalatest.freespec.AnyFreeSpec
import ECPT.PTW.CRC_hash_FSM
import chipsalliance.rocketchip.config.Parameters
import ECPT_Test.BoomTestUtils
import chiseltest._
import scala.util.control.Breaks._
import scala.collection.mutable.ArrayBuffer

class CRCTest extends AnyFreeSpec with ChiselScalatestTester {

    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams

    def removeBottom12Bits(arr: Array[Int]): Array[Int] = {
        val mask = 0xFFFFF000 // Mask with the lower 12 bits set to 0
        arr.map(value => value >>> 12)
    }

    "CRC should hash" in {
        test(new CRC_hash_FSM(12, 0x865, 27)(para)) { c =>

            val vaArray: Array[Int] = Array(0x80000000, 0x80001000, 0x80002000, 0x80003000, 0x80004000)
            val vpnArray = removeBottom12Bits(vaArray)
            val resultArray = ArrayBuffer[Int]()
            println(s"$resultArray")

            println("--------------start simulation----------")
            c.clock.step()
            println("-------------- end meaningless step ----------")
            for (element <- vpnArray){
                var hashed_val = c.io.data_out.peek()
                var done = c.io.done.peek()
                c.io.start.poke(false.B)
                c.clock.step()
                println(s"TEST: START")
                c.io.start.poke(true.B)
                c.io.data_in.poke(element.U)   
                c.clock.step() // one step to start
                c.io.start.poke(false.B)
                for (i <- 1 to 27){
                    println(s"TEST: test step $i")
                    c.clock.step()
                    hashed_val = c.io.data_out.peek()
                    done = c.io.done.peek()
                    if(done == true.B) 
                    println(s"done: $done")
                }
                c.io.done.expect(true.B)
                val result1Debug = c.io.data_out.peek()
                resultArray += result1Debug.litValue.toInt
                c.clock.step()

            }
            
            for (element <- resultArray){
                println(s"The crc debug is: $element 0x ${element.toHexString}")

            }

        }
    }
}

