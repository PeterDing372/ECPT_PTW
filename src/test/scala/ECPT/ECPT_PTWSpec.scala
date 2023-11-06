package ECPT.PTW

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._


class ECPTSpec extends AnyFreeSpec with ChiselScalatestTester{
    // implicit val para = (new DefaultConfig).toInstance
    implicit val para = (new myConfig(false)).toInstance

    def writePTWReq(PTW_Obj: MyPTWReq, addr: Int, need_gpa: Bool = false.B, vstage1: Bool = false.B, stage2: Bool = false.B) = {
        println(s"writePTWReq: addr: $addr\n")
        PTW_Obj.addr.poke("habcd".U(27.W))
    }

    "ECPTSpec should store TLB request" in {
        test(new ECPT_PTW(1)(para) ) { c =>
            println("SIMULATION: testing ECPT_PTW")
            val reqControl = c.io.requestor.req
            val debugs = c.io.debug
            // val  = c.io.requestor
            reqControl.valid.poke(0.B)     
            c.io.cache_valid.poke(0.B)  
            // advance the clock
            c.clock.step(1)  
            debugs.debug_state.expect(0.U)
            c.io.cache_valid.poke(1.B) 
            c.clock.step(1)   
            /* Starts */
            reqControl.bits.bits.addr.poke("habcd".U)
            reqControl.valid.poke(1.B) 
            /* s_hashing */
            println(s"SIMULATION: Entering s_hashing")
            c.clock.step(1) 
            reqControl.bits.bits.addr.poke(0.U) 
            debugs.debug_state.expect(1.U) // entering s_hashing
            println("SIMULATION: Start hashing")
            for(i <- 1 to 30){
            println(s"SIMULATION: hashing $i-th iteration")
            /* Expected 27 states before hashing is complete */
            debugs.req_addr.expect("habcd".U)
            c.clock.step(1) 
            }
            c.clock.step(5) 
        }

    }
    
}

// /* Testing out the counter */
// test(new ptwFSM) { c =>
//     println("--------------start simulation------------")
//     var testCount = c.io.debug_counter.peek()
//     var testState = c.io.debug_state.peek()
//     /* Init */
//     val TLBPTWIO_input = new TLBPTWIO
//     // c.io.requestor.req.valid.poke(false.B)
//     val reqControl = c.io.requestor.req 
//     // val  = c.io.requestor
//     reqControl.valid.poke(0.B)     
//     c.io.cache_valid.poke(0.B)  
//     // advance the clock
//     c.clock.step(1)  
//     c.io.debug_state.expect(0.U)
//     c.io.cache_valid.poke(1.B)   
//     c.clock.step(1)   
//     /* Starts */
//     reqControl.bits.bits.addr.poke("habcd".U)
//     reqControl.valid.poke(1.B) 
//     /* s_hashing */
//     println(s"SIMULATION: Entering s_hashing")
//     c.clock.step(1) 
//     reqControl.bits.bits.addr.poke(0.U) 
//     c.io.debug_state.expect(1.U) // entering s_hashing
//     println("SIMULATION: Start hashing")
//     for(i <- 1 to 30){
//       println(s"SIMULATION: hashing $i-th iteration")
//       /* Expected 27 states before hashing is complete */
//       c.clock.step(1) 
//     }
//     c.clock.step(5) 
    
// }
