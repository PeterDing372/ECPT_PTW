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
        // mem request
        // memIO.req.bits.phys := true.B
        // memIO.req.bits.cmd  := M_XRD // int load
        // memIO.req.bits.signed := false.B
        // memIO.req.bits.addr := pte_addr
        // memIO.req.bits.idx.foreach(_ := pte_addr)
        // memIO.req.bits.dprv := PRV.S.U   // PTW accesses are S-mode by definition
        // memIO.req.bits.dv := do_both_stages && !stage2
        // memIO.s1_kill := l2_hit || state =/= s_wait1
        // memIO.s2_kill := false.B
        println(s"[MemIO, req]: valid: ${memIO.req.valid.peek()} " +
          s"size: ${memIO.req.bits.size.peek()} " +
          s"addr: ${memIO.req.bits.addr.peek()} " +
          s"idx: ${memIO.req.bits.idx.foreach( _.peek())}" +
          s"dv: ${memIO.req.bits.dv.peek()}" +
          s"s1_kill ${memIO.s1_kill.peek()}")
        // println(s"[Debug Info, logic]: ptwState: ${debug.ptwState.peek()}")
        // println(s"[Debug Info, arbiter out]:addr: ${ArbOutMonitor.addr.peek()} " +
        //   s"need_gpa: ${ArbOutMonitor.need_gpa.peek()} " +
        //   s"vstage1: ${ArbOutMonitor.vstage1.peek()} " +
        //   s"stage2: ${ArbOutMonitor.stage2.peek()}")

    }

    def formRadixReqAddr(vpn2 : Int, vpn1 : Int, vpn0 : Int): Int = {
        // this form SV39 scheme VPN
        require(vpn2 < (1<<9) && vpn1 < (1<<9) && vpn0 < (1<<9))
        val finalVPN = (vpn2 << 18) | (vpn1 << 9) | vpn0
        finalVPN
    }

    def stepClock(dut : BOOM_PTW): Unit = {
        println(s"SIMULATION [Cycle $cycle]")
        cycle = cycle + 1
        dut.clock.step()
    }

    def formatPTE(reserved_for_future: Long,
                  ppn: Long,
                  reserved_for_software: Long = 0,
                  d: Long = 0,
                  a: Long = 0,
                  g: Long = 0,
                  u: Long = 0,
                  x: Long = 0,
                  w: Long = 0,
                  r: Long = 1,
                  v: Long = 0
                  ): UInt = {
      // var r_pte = UInt(64.W)
      val r_pte = ((reserved_for_future << 54) | 
                 (ppn << 10) | 
                 (reserved_for_software << 8) | 
                 (d << 7) | 
                 (a << 6) | 
                 (g << 5) | 
                 (u << 4) | 
                 (x << 3) | 
                 (w << 2) | 
                 (r << 1) | 
                 v)
      println(s"[formatPTE] r_pte ${r_pte.toBinaryString}")
      r_pte.U // & ~0.U(64.W)
    }



    val s_ready :: s_hashing :: s_traverse1 :: s_traverse2 :: s_req :: s_wait1 :: s_done :: Nil 
      = Enum(7)

    "BoomECPTSpec should hash complete within 28 cycles" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            /* use this section to test formatPTE  */
            // val test_result = formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val vpnAddr = formRadixReqAddr(1, 1, 1)
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
            memIO.resp.bits.data.poke(formatPTE(0x4, 0x1)) // true response
            for (i <- 0 until 4) {
              stepClock(c) 
            }
            // step without valid response
            memIO.resp.valid.poke(false)
            memIO.resp.bits.data.poke(formatPTE(0x3, 0xF)) // false response
            stepClock(c)  
            for (i <- 0 until 10) {
              stepClock(c) 
            }
            debug.ptwState.expect(s_traverse1)
            stepClock(c)
            // continue stepping to s_traverse2
            memIO.resp.valid.poke(true)
            memIO.resp.bits.data.poke(formatPTE(0x4, 0x1)) // true response
            for (i <- 0 until 4) {
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse2)
            memIO.resp.valid.poke(true)
            memIO.resp.bits.data.poke(formatPTE(0x7, 0x2)) // true response
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
            /* use this section to test formatPTE  */
            // val test_result = formatPTE(0, 1)
            // println(s"test_result ${test_result.litValue.toInt.toBinaryString}")
            cycle = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val vpnAddr = formRadixReqAddr(1, 1, 1)
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
              memIO.resp.bits.data.poke(formatPTE(0x4, i)) // true response
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_traverse2)
            stepClock(c)  
            stepClock(c)
            for (i <- 0 until 8) {
              memIO.resp.valid.poke(true)
              memIO.resp.bits.data.poke(formatPTE(0x7, i*2)) // true response
              stepClock(c) 
            }
            memIO.resp.valid.poke(false)
            stepClock(c) 
            debug.ptwState.expect(s_done)
            stepClock(c)

        }
    }
    
    
}
