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



class PTWSkeletonSpec extends AnyFreeSpec with ChiselScalatestTester{



    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams
    var cycle = 0
    val base_state_num = 8
    // val s_ready :: s_req :: s_wait1 :: s_dummy1 :: s_wait2 :: s_wait3 :: s_dummy2 :: s_fragment_superpage :: Nil 
    //   = Enum(base_state_num)
    val s_ready :: s_hashing :: s_traverse0 :: s_traverse1 :: s_req :: s_wait1 :: Nil 
      = Enum(6)
        

    def writePTWReq(reqObj: DecoupledIO[Valid[PTWReq]], addr: Int, 
    need_gpa: Bool = false.B, vstage1: Bool = false.B, stage2: Bool = false.B) = {
        println(s"[writePTWReq]: addr: ${addr.toBinaryString} decimal: ${addr}")
        reqObj.valid.poke(true)
        reqObj.bits.valid.poke(true)
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
          s"pte_addr: ${other.line_addr.peek()}")

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

    /*
    * This section is commented out for cleanliness
    * ------------------------------------------
    * Test case for BOOM Page Table Walker (PTW) compilation
    * 
    * "PTWSkeleton should compile" in {
    *     test(new BOOM_PTW(1)(para)) { c =>
    *         println("SIMULATION [DONE]: compiled successfully")
    *     }
    * }
    * ------------------------------------------
    */

    "PTWSkeleton should get request" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            cycle = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val vpnAddr = formRadixReqAddr(1, 1, 1)
            println(s"SIMULATION [vpnAddr]: hex ${vpnAddr.toHexString} /  ${vpnAddr.toBinaryString}")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val memIO = c.io.mem
            requestor(0).req.valid.poke(false)
            printDebugInfo(debug)
            printMemIO(memIO)
            stepClock(c)
            stepClock(c)
            printDebugInfo(debug)
            debug.ptwState.expect(s_ready)
            stepClock(c)
            printDebugInfo(debug)
            writePTWReq(requestor(0).req, vpnAddr, stage2 = true.B)
            stepClock(c)
            debug.ptwState.expect(s_req)
            printDebugInfo(debug)
            printMemIO(memIO)
            stepClock(c)
            // assume cache received request and set resp is ready
            setCacheReqState(memIO, true.B)
            printDebugInfo(debug)
            printMemIO(memIO)
            stepClock(c)
            printDebugInfo(debug)
            printMemIO(memIO)
            debug.ptwState.expect(s_wait1)
            stepClock(c)
            printDebugInfo(debug)
            printMemIO(memIO)
            

        }
    }
    /* Note request does not stay on arb out if we remove the request */
    
    
}
