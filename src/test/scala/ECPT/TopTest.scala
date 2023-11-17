package ECPT.PTW.TOP

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import freechips.rocketchip.tile.TileKey
import freechips.rocketchip.tile.RocketTileParams
import chipsalliance.rocketchip.config._
import ECPT.Params._
import ECPT_Test._
import ECPT.PTW._



class MyTopSpec extends AnyFreeSpec with ChiselScalatestTester{
    // implicit val para = (new DefaultConfig).toInstance
    // val initial_param = (new myConfig(false).toInstance.alterMap(Map(TileKey -> RocketTileParams)))
    // val initial_param = (new myConfig(false).toInstance.alterPartial{case TileKey => RocketTileParams})
    


    val boomParams: Parameters = RocketTestUtils.getRocketParameters("DefaultConfig")
    implicit val para: Parameters = boomParams
        

    "MyTopSpec should compile" in {
        test(new myTop()(para) ) { c =>
            println("SIMULATION: testing MyTop")
            
            
        }

    }

    // "ECPT_PTW should store fetched cache response" in {
    //     test(new ECPT_PTW(1)(para) ) { c =>
    //         println("SIMULATION: testing ECPT_PTW")
    //         val TLBReq= c.io.requestor.req
    //         val debugs = c.io.debug
    //         val cacheIO = c.io.mem
    //         TLBReq.valid.poke(0.B)     
    //         cacheIO.resp.valid.poke(0.B)  
    //         // advance the clock
    //         c.clock.step(1)  
    //         debugs.debug_state.expect(0.U)
    //         cacheIO.resp.valid.poke(1.B) 
    //         c.clock.step(1)   
    //         /* Starts */
    //         TLBReq.valid.poke(1.B) 
    //         TLBReq.bits.bits.addr.poke(1.U) // poke 1 addr
    //         /* s_hashing */
    //         println(s"SIMULATION: Entering s_hashing")
    //         c.clock.step(1) 
    //         TLBReq.bits.bits.addr.poke(0.U) // clear input
    //         TLBReq.valid.poke(0.B) 
    //         debugs.debug_state.expect(1.U) // entering s_hashing
    //         println("SIMULATION: Start hashing")
    //         for(i <- 1 to 30){
    //         println(s"SIMULATION: hashing $i-th iteration")
    //         /* Expected 27 states before hashing is complete */
    //         debugs.req_addr.expect("habcd".U)
    //         c.clock.step(1) 
    //         }

    //         c.clock.step(5) 
    //     }

    // }
    
}
