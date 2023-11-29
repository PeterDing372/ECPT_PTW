package ECPT.PTW

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
import ECPT.Units._



class traverseLineSpec extends AnyFreeSpec with ChiselScalatestTester{



    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams
    var cycle = 0

    val util = ECPTTestUtils

    "traverseLine should update line" in {
        test(new traverseLine()(para) ) { c =>
            
            c.clock.step()
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)
            c.io.data_in.valid.poke(true.B)
            c.io.data_in.bits.poke(1.U)
            c.clock.step()
            c.io.data_in.valid.poke(true.B)
            c.io.data_in.bits.poke(2.U)
            c.clock.step()
            c.io.data_in.valid.poke(false.B)
            c.io.data_in.bits.poke("hffff_ffff".U)
            c.clock.step()
            c.io.data_in.valid.poke(true.B)
            c.io.data_in.bits.poke("h0F".U)
            c.clock.step()
            c.io.data_in.valid.poke(false.B)
            c.clock.step()

        }
    }

    "traverseLine should fetch tag" in {
        test(new traverseLine()(para) ) { c =>
            var tag : Long = 0
            c.clock.step()
            c.io.start.poke(true.B)
            c.clock.step()
            c.io.start.poke(false.B)
            for (i <- 0 until 8) {
                println(s"write cycle ${i}")
                c.io.data_in.valid.poke(true.B)
                c.io.data_in.bits.poke(util.formatPTE(0x2, 0x1))
                if (i < 5) {
                    tag = tag | 0x2.toLong << (3 * i)
                } else {
                    tag = tag | 0x2.toLong << (15 + 4 * (i-5))
                }
                c.io.tag_in.poke(tag.U)
                println(s"[TEST] tag_in: ${tag.toBinaryString}")
                c.clock.step()
                println(s"[TEST] current tag: ${c.io.debug.tag.peek().litValue.toInt.toBinaryString}")
                c.io.debug.tagMatch.expect(true)
            }
            c.clock.step()
        }
    }

    /**
     * Note:
     * Flush is not required as only full fetch occurs and the tag is compared after a full fetch.
     */



  
    
}
