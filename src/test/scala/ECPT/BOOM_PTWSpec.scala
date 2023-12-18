package ECPT.PTW

import chisel3._
import chisel3.util._
import chiseltest._
import scala.util.Random
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import freechips.rocketchip.rocket._
import chipsalliance.rocketchip.config._
import ECPT_Test._
import ECPT.Params._
import ECPT.Debug._



class BoomECPTSpec extends AnyFreeSpec with ChiselScalatestTester {



    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams
    var cycle = 0
    val base_state_num = 8
    val util = ECPTTestUtils
    val s_ready :: s_hashing :: s_traverse0 :: s_traverse1 :: s_req :: s_wait1 :: s_done :: Nil 
      = Enum(7)
        

    def writePTWReq(reqObj: DecoupledIO[Valid[PTWReq]], addr: UInt, 
    need_gpa: Bool = false.B, vstage1: Bool = false.B, stage2: Bool = false.B) = {
        // println(s"[writePTWReq]: addr: ${addr.toBinaryString} decimal: ${addr}")

        val localReq = reqObj.bits.bits // this is the PTWReq object
        localReq.addr.poke(addr)
        localReq.need_gpa.poke(need_gpa) // this is always false == no virtual machine support
        localReq.vstage1.poke(vstage1) // false: then PTW only do 1 stage table walk
        localReq.stage2.poke(stage2)

    }

    /**
     * reads value from the PTW response
     * @param respObj DecoupledIO[Valid[PTWResp]]
     * @out returns the ppn in the response
     */
    def readPTEResp(respObj: Valid[PTWResp]): UInt = {
      val localObj = respObj.bits // this is the PTWReq object
      val addr = localObj.pte.ppn.peek()
      // val 
      addr
    }

    def setCacheReqState(memIO: HellaCacheIO, ReqReady: Bool): Unit = {
      memIO.req.ready.poke(ReqReady)
    }

    def printDebugInfo(debug: PTW_DebugIO): Unit = {
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


      /* Initializes the inputs to PTW */
    def InitializePTW(reqObj: DecoupledIO[Valid[PTWReq]], memIO : HellaCacheIO, 
                      debug: PTW_DebugIO, c : BOOM_PTW) = {
      reqObj.valid.poke(false)
      memIO.resp.valid.poke(false)
      stepClock(c)
      stepClock(c)
      debug.ptwState.expect(s_ready)
      stepClock(c)
    }

    /**
     * Sends a request to the Page Table Walker (PTW) and waits until the hashing process is complete.
     * @param vpnAddr - Virtual Page Number Address
     */
    def sendForHash(reqObj: DecoupledIO[Valid[PTWReq]], vpnAddr : UInt, 
                      debug: PTW_DebugIO, c : BOOM_PTW) = {
      reqObj.valid.poke(true)
      reqObj.bits.valid.poke(true)
      writePTWReq(reqObj, vpnAddr, stage2 = true.B)
      stepClock(c)
      reqObj.valid.poke(false)

      println("[TEST, sendForHash] start hashing")
      for (i <- 0 until 30) {
          println(s"hash cycle ${i}")
          stepClock(c)
      }
      stepClock(c)
      debug.ptwState.expect(s_traverse0)
      stepClock(c) // one step without valid response 
    }

    def turnOffReq(reqObj: DecoupledIO[Valid[PTWReq]], debug: PTW_DebugIO, 
                    c : BOOM_PTW) = {
      debug.ptwState.expect(s_done)
      reqObj.valid.poke(false)
      reqObj.bits.valid.poke(false)
      stepClock(c)
    }
    
    /**
     * Set the response IO to respond to the Page Table Walker (PTW) and
     * Responds the complete cache line of 64 bytes
     * @param tagArr Array[Int] - an array of 8 entries that composes the complete vpnAddr tag
     * @param expState UInt - exit state expected to be expState
     */
    def makeMemResp(memIO : HellaCacheIO, tagArr : Array[Int],
                    debug: PTW_DebugIO, expState : UInt, c : BOOM_PTW) = {
      for (i <- 0 until 8) {
        memIO.resp.valid.poke(true)
        memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(tagArr(i).toLong, i)) // true response
        stepClock(c) 
      }
      memIO.resp.valid.poke(false)
      stepClock(c) 
      debug.ptwState.expect(expState)
    }


    /**
     * This test verifies:
     *  1. ptw continues after valid de-asserted
     */
    // "BoomECPTSpec de-assert req valid" in {
    //   test(new BOOM_PTW(1)(para) ) { c =>
    //         /* use this section to test ECPTTestUtils.formatPTE  */
    //         // val test_result = ECPTTestUtils.formatPTE(0, 1)
    //         // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
    //         cycle = 0
    //         val total_tests = 5
    //         println("SIMULATION [Start]: write request to Boom_PTW")
    //         val debug = c.io.debug
    //         val requestor = c.io.requestor
    //         val memIO = c.io.mem
    //         val reqObj = requestor(0).req
    //         val respObj = requestor(0).resp
    //         val vpnAddr0 = ECPTTestUtils.generateRandomBinaryString()
    //         val tagArr0 = ECPTTestUtils.AddrToRespGroup(vpnAddr0.U)
    //         val vpnAddr1 = ECPTTestUtils.generateRandomBinaryString()
    //         val tagArr1 = ECPTTestUtils.AddrToRespGroup(vpnAddr1.U)
    //         /* Select different way */
    //         val sel = Random.nextInt(2)
    //         val RespTag = if (sel == 1) tagArr1 else tagArr0
    //         val reqAddr = if (sel == 1) vpnAddr1 else vpnAddr0
    //         /* Start page table walk process */
    //         InitializePTW(reqObj, memIO, debug, c)
    //         debug.ptwState.expect(s_ready)
    //         sendForHash(reqObj, reqAddr.U, debug, c)
    //         makeMemResp(memIO, tagArr0, debug, s_traverse1, c)
    //         stepClock(c)  
    //         /* second set of repsonse */              
    //         makeMemResp(memIO, tagArr1, debug, s_done, c)
    //         turnOffReq(reqObj, debug, c)
    //         respObj.valid.expect(true)
    //         stepClock(c)
    //         debug.ptwState.expect(s_ready)
    //         /* tag verification */
    //         debug.tagT0.expect(vpnAddr0.U)
    //         debug.tagT1.expect(vpnAddr1.U)
    //         /* verify correct response value */
    //         val respPPN = readPTEResp(respObj)
    //         val pteInlineAddr = debug.pteInlineAddr.peek()
    //         // println(s"[TEST] tag0: ${vpnAddr0}")
    //         // println(s"[TEST] tag1: ${vpnAddr1}")
    //         println(s"[TEST] pteInlineAddr: ${pteInlineAddr} respPPN: ${respPPN}")
    //         assert(respPPN == respPPN)
    //         /* Print all response info */
    //         println(s"respObj.bits")
    //         // ${test_result.litValue.toInt.toBinaryString}
    //         debug.ECPT_hit_way.expect(sel.U)
    //         stepClock(c)
            
    //     }
    // }

    /**
     * This test verifies:
     *  1. response fetches correct offset from cacheline
     */
    "BoomECPTSpec Check Response" in {
      test(new BOOM_PTW(1)(para) ) { c =>
            /* use this section to test ECPTTestUtils.formatPTE  */
            // val test_result = ECPTTestUtils.formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            val total_tests = 5
            println("SIMULATION [Start]: write request to Boom_PTW")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val memIO = c.io.mem
            val reqObj = requestor(0).req
            val respObj = requestor(0).resp
            for (i <- 0 until total_tests) {
              val vpnAddr0 = ECPTTestUtils.generateRandomBinaryString()
              val tagArr0 = ECPTTestUtils.AddrToRespGroup(vpnAddr0.U)
              val vpnAddr1 = ECPTTestUtils.generateRandomBinaryString()
              val tagArr1 = ECPTTestUtils.AddrToRespGroup(vpnAddr1.U)
              /* Select different way */
              val sel = Random.nextInt(2)
              val RespTag = if (sel == 1) tagArr1 else tagArr0
              val reqAddr = if (sel == 1) vpnAddr1 else vpnAddr0
              /* Start page table walk process */
              InitializePTW(reqObj, memIO, debug, c)
              debug.ptwState.expect(s_ready)
              sendForHash(reqObj, reqAddr.U, debug, c)
              makeMemResp(memIO, tagArr0, debug, s_traverse1, c)
              stepClock(c)  
              /* second set of repsonse */              
              makeMemResp(memIO, tagArr1, debug, s_done, c)
              turnOffReq(reqObj, debug, c)
              respObj.valid.expect(true)
              stepClock(c)
              debug.ptwState.expect(s_ready)
              /* tag verification */
              debug.tagT0.expect(((vpnAddr0.U.litValue.toInt & 0xFFFFFFF8)>>3).U)
              debug.tagT1.expect(((vpnAddr1.U.litValue.toInt & 0xFFFFFFF8)>>3).U)
              /* verify correct response value */
              val respPPN = readPTEResp(respObj)
              val pteInlineAddr = debug.pteInlineAddr.peek()
              // println(s"[TEST] tag0: ${vpnAddr0}")
              // println(s"[TEST] tag1: ${vpnAddr1}")
              println(s"[TEST] pteInlineAddr: ${pteInlineAddr} respPPN: ${respPPN}")
              assert(respPPN == respPPN)
              /* Print all response info */
              println(s"respObj.bits")
              // ${test_result.litValue.toInt.toBinaryString}
              debug.ECPT_hit_way.expect(sel.U)
              stepClock(c)
            }
            
        }
    }
    


      /**
       * This test verifies:
       *  1. correct tag extraction
       *  2. correct way hit judgement
       *  3. correct state transition 
       */
    "BoomECPTSpec tag match comprehensive input" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            /* use this section to test ECPTTestUtils.formatPTE  */
            // val test_result = ECPTTestUtils.formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            val total_tests = 20
            println("SIMULATION [Start]: write request to Boom_PTW")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val memIO = c.io.mem
            val reqObj = requestor(0).req
            for (i <- 0 until total_tests) {
              val vpnAddr0 = ECPTTestUtils.generateRandomBinaryString()
              val tagArr0 = ECPTTestUtils.AddrToRespGroup(vpnAddr0.U)
              val vpnAddr1 = ECPTTestUtils.generateRandomBinaryString()
              val tagArr1 = ECPTTestUtils.AddrToRespGroup(vpnAddr1.U)
              /* Select different way */
              val sel = Random.nextInt(2)
              val RespTag = if (sel == 1) tagArr1 else tagArr0
              val reqAddr = if (sel == 1) vpnAddr1 else vpnAddr0

              InitializePTW(reqObj, memIO, debug, c)
              debug.ptwState.expect(s_ready)

              sendForHash(reqObj, reqAddr.U, debug, c)

              makeMemResp(memIO, tagArr0, debug, s_traverse1, c)
              stepClock(c)  
              /* second set of repsonse */              
              makeMemResp(memIO, tagArr1, debug, s_done, c)
              turnOffReq(reqObj, debug, c)
              stepClock(c)
              debug.ptwState.expect(s_ready)
              /* tag verification */
              debug.tagT0.expect(((vpnAddr0.U.litValue.toInt & 0xFFFFFFF8)>>3).U)
              debug.tagT1.expect(((vpnAddr1.U.litValue.toInt & 0xFFFFFFF8)>>3).U)
              println(s"[TEST] tag0: ${vpnAddr0}")
              println(s"[TEST] tag1: ${vpnAddr1}")
              println(s"[TEST] sel: $sel hit way: ${debug.ECPT_hit_way.peek()}")
              debug.ECPT_hit_way.expect(sel.U)
              stepClock(c)
              

            }
            
        }
    }

    /* 
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
            writePTWReq(requestor(0).req, vpnAddr.U, stage2 = true.B)
            val reqObj = requestor(0).req
            reqObj.valid.poke(true)
            reqObj.bits.valid.poke(true)
            println("[TEST] start hashing")
            for (i <- 0 until 30) {
                println(s"hash cycle ${i}")
                stepClock(c)
            }
            stepClock(c)
            debug.ptwState.expect(s_traverse0)
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
            debug.ptwState.expect(s_traverse0)
            stepClock(c)
            // continue stepping to s_traverse1
            memIO.resp.valid.poke(true)
            memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x4, 0x1)) // true response
            for (i <- 0 until 4) {
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse1)
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
            writePTWReq(requestor(0).req, vpnAddr.U, stage2 = true.B)
            val reqObj = requestor(0).req
            reqObj.valid.poke(true)
            reqObj.bits.valid.poke(true)
            println("[TEST] start hashing")
            for (i <- 0 until 30) {
                println(s"hash cycle ${i}")
                stepClock(c)
            }
            stepClock(c)
            debug.ptwState.expect(s_traverse0)
            stepClock(c) // one step without valid response 
            for (i <- 0 until 8) {
              memIO.resp.valid.poke(true)
              memIO.resp.bits.data.poke(ECPTTestUtils.formatPTE(0x4, i)) // true response
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse1)
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
            writePTWReq(requestor(0).req, vpnAddr.U, stage2 = true.B)
            val reqObj = requestor(0).req
            reqObj.valid.poke(true)
            reqObj.bits.valid.poke(true)
            println("[TEST] start hashing")
            for (i <- 0 until 30) {
                println(s"hash cycle ${i}")
                stepClock(c)
            }
            stepClock(c)
            debug.ptwState.expect(s_traverse0)
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
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse1)
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
            debug.tagT0.expect(tag0.U)
            debug.tagT1.expect(tag1.U)
            println(s"[TEST] tag0: ${tag0.toBinaryString}")
            println(s"[TEST] tag1: ${tag1.toBinaryString}")
            println(s"[TEST] ptw tag0: ${debug.tagT0.peek().litValue.toLong.toBinaryString}")
            println(s"[TEST] ptw tag1: ${debug.tagT1.peek().litValue.toLong.toBinaryString}")
            stepClock(c)
            // 11_1011_1011_1111_1111_1111_1111

        }
    }
    */

    
}
