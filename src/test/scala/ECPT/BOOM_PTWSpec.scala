package ECPT.PTW

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import ECPT.Params._
import freechips.rocketchip.tile.TileKey
import freechips.rocketchip.tile.RocketTileParams
import chipsalliance.rocketchip.config._
import ECPT_Test._
import freechips.rocketchip.rocket._
import chisel3.util._


class BoomECPTSpec extends AnyFreeSpec with ChiselScalatestTester{
    // implicit val para = (new DefaultConfig).toInstance
    // val initial_param = (new myConfig(false).toInstance.alterMap(Map(TileKey -> RocketTileParams)))
    // val initial_param = (new myConfig(false).toInstance.alterPartial{case TileKey => RocketTileParams})
    


    val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams
        

    def writePTWReq(reqObj: DecoupledIO[Valid[PTWReq]], addr: Int, 
    need_gpa: Bool = false.B, vstage1: Bool = false.B, stage2: Bool = false.B) = {
        println(s"[writePTWReq]: addr: $addr")
        reqObj.valid.poke(true)
        val localReq = reqObj.bits.bits // this is the PTWReq object
        localReq.addr.poke(addr.U(27.W))
        localReq.need_gpa.poke(need_gpa) // this is always false == no virtual machine support
        localReq.vstage1.poke(vstage1) // false: then PTW only do 1 stage table walk
        localReq.stage2.poke(stage2)

    }

    "BoomECPTSpec should compile" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            println("SIMULATION[DONE]: compiled succesfully")          
        }
    }

    "BoomECPTSpec should get request" in {
        test(new BOOM_PTW(1)(para) ) { c =>
            println("SIMULATION [Start]: write request to Boom_PTW")
            val debug = c.io.debug
            val requestor = c.io.requestor
            val PTWReqMonitor = debug.r_req_input
            requestor(0).req.valid.poke(false)
            c.clock.step()
            println(s"PTWReqMonitor Info: addr: ${PTWReqMonitor.addr.peek()}")
            c.clock.step()
            writePTWReq(requestor(0).req, 1, stage2 = true.B)
            c.clock.step()
            // PTWReqMonitor.addr = debug.r_req_input.addr.peek()
            // PTWReqMonitor.need_gpa = debug.r_req_input.need_gpa.peek()
            // PTWReqMonitor.vstage1 = debug.r_req_input.vstage1.peek()
            // PTWReqMonitor.stage2 = debug.r_req_input.stage2.peek()
            println(s"PTWReqMonitor Info: addr: ${PTWReqMonitor.addr.peek()}")
            
        }

    }

    // class PTWReq(implicit p: Parameters) extends CoreBundle()(p) {
    //     val addr = UInt(vpnBits.W)
    //     val need_gpa = Bool()
    //     val vstage1 = Bool()
    //     val stage2 = Bool()
    // }
    
}
