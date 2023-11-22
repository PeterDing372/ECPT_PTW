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

import java.io.{File, PrintStream}

class BoomECPTSpec extends AnyFreeSpec with ChiselScalatestTester{
    val file = new File("log.txt")
    val printStream = new PrintStream(file)
    System.setOut(printStream)
    


    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams
    var cycle = 0
        

    def writePTWReq(reqObj: DecoupledIO[Valid[PTWReq]], addr: Int, 
    need_gpa: Bool = false.B, vstage1: Bool = false.B, stage2: Bool = false.B) = {
        println(s"[writePTWReq]: addr: $addr")
        reqObj.valid.poke(true)
        reqObj.bits.valid.poke(true)
        val localReq = reqObj.bits.bits // this is the PTWReq object
        localReq.addr.poke(addr.U(27.W))
        localReq.need_gpa.poke(need_gpa) // this is always false == no virtual machine support
        localReq.vstage1.poke(vstage1) // false: then PTW only do 1 stage table walk
        localReq.stage2.poke(stage2)

    }

    def printDebugInfo(debug: BOOM_PTW_DebugIO): Unit = {
        val PTWReqMonitor = debug.r_req_input
        val ArbOutMonitor = debug.r_req_arb
        println(s"[Info, input]: addr: ${PTWReqMonitor.addr.peek()} " +
          s"need_gpa: ${PTWReqMonitor.need_gpa.peek()} " +
          s"vstage1: ${PTWReqMonitor.vstage1.peek()} " +
          s"stage2: ${PTWReqMonitor.stage2.peek()}" +
          s"valid: ${}")
        println(s"[Info, logic]: ptwState: ${debug.ptwState.peek()}")
        println(s"[Info, arbiter out]:addr: ${ArbOutMonitor.addr.peek()} " +
          s"need_gpa: ${ArbOutMonitor.need_gpa.peek()} " +
          s"vstage1: ${ArbOutMonitor.vstage1.peek()} " +
          s"stage2: ${ArbOutMonitor.stage2.peek()}")

    }

    def stepClock(dut : BOOM_PTW): Unit = {
        println(s"SIMULATION [Cycle $cycle]")
        cycle = cycle + 1
        dut.clock.step()
    }

    "BoomECPTSpec should compile" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            println("SIMULATION [DONE]: compiled succesfully")          
        }
    }

    "BoomECPTSpec should get request" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            cycle = 0
            println("SIMULATION [Start]: write request to Boom_PTW")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val PTWReqMonitor = debug.r_req_input
            val ArbOutMonitor = debug.r_req_arb
            requestor(0).req.valid.poke(false)
            printDebugInfo(debug)
            stepClock(c)
            stepClock(c)
            printDebugInfo(debug)
            debug.ptwState.expect(0.U)
            stepClock(c)
            printDebugInfo(debug)
            writePTWReq(requestor(0).req, 1, stage2 = true.B)
            stepClock(c)
            printDebugInfo(debug)
            stepClock(c)
            printDebugInfo(debug)
            stepClock(c)
            printDebugInfo(debug)

            
        }

    }

    // class PTWReq(implicit p: Parameters) extends CoreBundle()(p) {
    //     val addr = UInt(vpnBits.W)
    //     val need_gpa = Bool()
    //     val vstage1 = Bool()
    //     val stage2 = Bool()
    // }
    
}
