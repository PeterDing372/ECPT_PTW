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



class BoomECPTSpec extends AnyFreeSpec with ChiselScalatestTester{



    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams
    var cycle = 0
    val base_state_num = 8
        

    def writePTWReq(reqObj: DecoupledIO[Valid[PTWReq]], addr: Int, 
    need_gpa: Bool = false.B, vstage1: Bool = false.B, stage2: Bool = false.B) = {
        println(s"[writePTWReq]: addr: ${addr.toBinaryString} decimal: ${addr}")

        val localReq = reqObj.bits.bits // this is the PTWReq object
        localReq.addr.poke(addr.U(27.W))
        localReq.need_gpa.poke(need_gpa) // this is always false == no virtual machine support
        localReq.vstage1.poke(vstage1) // false: then PTW only do 1 stage table walk
        localReq.stage2.poke(stage2)

    }

    def setCacheReqState(memIO: HellaCacheIO, ReqReady: Bool): Unit = {
      memIO.req.ready.poke(ReqReady)
    }

    def printDebugInfo(debug: BOOM_PTW_DebugIO): Unit = {
        val PTWReqMonitor = debug.r_req_input
        val ArbOutMonitor = debug.r_req_arb
        val other = debug.other_logic
        println(s"[Debug: input]: addr: ${PTWReqMonitor.addr.peek()} " +
          s"need_gpa: ${PTWReqMonitor.need_gpa.peek()} " +
          s"vstage1: ${PTWReqMonitor.vstage1.peek()} " +
          s"stage2: ${PTWReqMonitor.stage2.peek()}" +
          s"valid: ${}")
        println(s"[Debug: logic]: ptwState: ${debug.ptwState.peek()}")
        println(s"[Debug: arbiter out]:addr: ${ArbOutMonitor.addr.peek()} " +
          s"need_gpa: ${ArbOutMonitor.need_gpa.peek()} " +
          s"vstage1: ${ArbOutMonitor.vstage1.peek()} " +
          s"stage2: ${ArbOutMonitor.stage2.peek()}")
        println(s"[Debug: other logic]:vpn: ${other.vpn.peek()} " +
          s"do_both_stages: ${other.do_both_stages.peek()} " +
          s"line_addr: ${other.line_addr.peek()}")

    }

    def printMemIO(memIO: HellaCacheIO): Unit = {
        println(s"[MemIO, req]: valid: ${memIO.req.valid.peek()} " +
          s"size: ${memIO.req.bits.size.peek()} " +
          s"addr: ${memIO.req.bits.addr.peek()} " +
          s"idx: ${memIO.req.bits.idx.foreach( _.peek())}" +
          s"dv: ${memIO.req.bits.dv.peek()}" +
          s"s1_kill ${memIO.s1_kill.peek()}")
    }

    def stepClock(dut : BOOM_PTW): Unit = {
        println(s"SIMULATION [Cycle $cycle]")
        cycle = cycle + 1
        dut.clock.step()
    }

    val util = ECPTTestUtils

    val s_ready :: s_hashing :: s_traverse1 :: s_traverse2 :: s_req :: s_wait1 :: s_done :: Nil 
      = Enum(7)

    "BoomECPTSpec should hash complete within 28 cycles" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            /* use this section to test ECPTTestUtils.formatPTE  */
            // val test_result = ECPTTestUtils.formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val vpnAddr = ECPTTestUtils.formRadixReqAddr(1, 1, 1)
            println(s"SIMULATION [vpnAddr]: hex ${vpnAddr.toHexString} /  ${vpnAddr.toBinaryString}")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val memIO = c.io.mem
            requestor(0).req.valid.poke(false)
            memIO.resp.valid.poke(false)
            // printDebugInfo(debug)
            // printMemIO(memIO)
            stepClock(c)
            stepClock(c)
            // printDebugInfo(debug)
            debug.ptwState.expect(s_ready)
            stepClock(c)
            // printDebugInfo(debug)
            writePTWReq(requestor(0).req, vpnAddr, stage2 = true.B)
            val reqObj = requestor(0).req
            reqObj.valid.poke(true)
            reqObj.bits.valid.poke(true)
            println("[TEST] start hashing")
            for (i <- 0 until 30) {
                println(s"hash cycle ${i}")
                stepClock(c)
            }
            stepClock(c)
            debug.ptwState.expect(s_traverse1)
            stepClock(c) // one step without valid response 
            memIO.resp.valid.poke(true)
            memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x4, 0x1)) // true response
            for (i <- 0 until 4) {
              stepClock(c) 
            }
            // step without valid response
            memIO.resp.valid.poke(false)
            memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x3, 0xF)) // false response
            stepClock(c)  
            for (i <- 0 until 10) {
              stepClock(c) 
            }
            debug.ptwState.expect(s_traverse1)
            stepClock(c)
            // continue stepping to s_traverse2
            memIO.resp.valid.poke(true)
            memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x4, 0x1)) // true response
            for (i <- 0 until 4) {
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse2)
            memIO.resp.valid.poke(true)
            memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x7, 0x2)) // true response
            for (i <- 0 until 8) {
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_done)
            stepClock(c)

            // printDebugInfo(debug)
            // printMemIO(memIO)

        }
    }
    

    "BoomECPTSpec reg store fetched data" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            /* use this section to test ECPTTestUtils.formatPTE  */
            // val test_result = ECPTTestUtils.formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            var tag : Long = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val vpnAddr = ECPTTestUtils.formRadixReqAddr(1, 1, 1)
            println(s"SIMULATION [vpnAddr]: hex ${vpnAddr.toHexString} /  ${vpnAddr.toBinaryString}")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val memIO = c.io.mem
            requestor(0).req.valid.poke(false)
            memIO.resp.valid.poke(false)
            stepClock(c)
            stepClock(c)
            debug.ptwState.expect(s_ready)
            stepClock(c)
            writePTWReq(requestor(0).req, vpnAddr, stage2 = true.B)
            val reqObj = requestor(0).req
            reqObj.valid.poke(true)
            reqObj.bits.valid.poke(true)
            println("[TEST] start hashing")
            for (i <- 0 until 30) {
                println(s"hash cycle ${i}")
                stepClock(c)
            }
            stepClock(c)
            debug.ptwState.expect(s_traverse1)
            stepClock(c) // one step without valid response 
            for (i <- 0 until 8) {
              memIO.resp.valid.poke(true)
              memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x4, i)) // true response
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse2)
            stepClock(c)  
            stepClock(c)
            for (i <- 0 until 8) {
              memIO.resp.valid.poke(true)
              memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x7, i*2)) // true response
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_done)
            stepClock(c)

        }
    }


    "BoomECPTSpec tag hit" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            /* use this section to test ECPTTestUtils.formatPTE  */
            // val test_result = ECPTTestUtils.formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            var tag0 : Long = 0
            var tag1 : Long = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val vpnAddr = ECPTTestUtils.formRadixReqAddr(1, 1, 1)
            println(s"SIMULATION [vpnAddr]: hex ${vpnAddr.toHexString} /  ${vpnAddr.toBinaryString}")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val memIO = c.io.mem
            requestor(0).req.valid.poke(false)
            memIO.resp.valid.poke(false)
            stepClock(c)
            stepClock(c)
            debug.ptwState.expect(s_ready)
            stepClock(c)
            writePTWReq(requestor(0).req, vpnAddr, stage2 = true.B)
            val reqObj = requestor(0).req
            reqObj.valid.poke(true)
            reqObj.bits.valid.poke(true)
            println("[TEST] start hashing")
            for (i <- 0 until 30) {
                println(s"hash cycle ${i}")
                stepClock(c)
            }
            stepClock(c)
            debug.ptwState.expect(s_traverse1)
            stepClock(c) // one step without valid response 
            for (i <- 0 until 8) {
              memIO.resp.valid.poke(true)
              memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x4, i)) // true response
              stepClock(c) 
              if (i < 5) {
                tag0 = tag0 | 0x4.toLong << (3 * i)
              } else {
                tag0 = tag0 | 0x4.toLong << (15 + 4 * (i-5))
              }
            }
            println(s"[TEST] tag0: ${tag0.toBinaryString}")
            println(s"[TEST] tag1: ${tag1.toBinaryString}")
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse2)
            stepClock(c)  
            stepClock(c)
            for (i <- 0 until 8) {
              memIO.resp.valid.poke(true)
              memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x7, i*2)) // true response
              stepClock(c) 
              if (i < 5) {
                tag1 = tag1 | 0x7.toLong << (3 * i)
              } else {
                tag1 = tag1 | 0x7.toLong << (15 + 4 * (i-5))
              }
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_done)
            debug.tagT1.expect(tag0.U)
            debug.tagT1.expect(tag1.U)
            stepClock(c)

        }
    }
    
    
}
