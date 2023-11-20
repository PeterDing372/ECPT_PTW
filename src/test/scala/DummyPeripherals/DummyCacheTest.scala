package ECPT.DummmyPeriphrals

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import ECPT.Params._


class DummyCacheSpec extends AnyFreeSpec with ChiselScalatestTester{
    // implicit val para = (new DefaultConfig).toInstance
    implicit val para = (new myConfig(true)).toInstance


    "DummyCache should Store WDATA" in {
        test(new DummyCache()(para) ) { c =>
            println("SIMULATION: testing DummyCache")
            val reader = c.io.ptw
            val writer = c.io.cpu
            c.clock.step(1) // do nothing
            // val ptw = Flipped(new MyHellaCacheIO) // input
            // val cpu = Flipped(Decoupled(new MyCacheDataReq))
            /* Write sequentially a whole cacheline */
            for (i <- 0 to 7) {
                writer.valid.poke(1)
                writer.bits.write.poke(1)
                writer.bits.wdata.poke(1+i)
                writer.bits.addr.poke(i)
                c.clock.step(1) 
            }
            /* Cancel write requests */
            writer.valid.poke(0)
            writer.bits.write.poke(0)
            writer.bits.wdata.poke(11)
            writer.bits.addr.poke(2)
            c.clock.step(1) 
            /* Read and see if I can get correct value */ 
            reader.req.bits.addr.poke(2.U)
            val readVal = reader.resp.bits.data.peek()
            c.clock.step(1) 
            println(s"ReadVal $readVal")
            
        }

    }

    // "DummyCache should have correct tag" in {
    //     test(new DummmyCache()(para) ) { c =>
    //         println("SIMULATION: testing DummyCache")
    //         val reader = c.io.ptw
    //         val writer = c.io.cpu
    //         c.clock.step(1) // do nothing
    //         // val ptw = Flipped(new MyHellaCacheIO) // input
    //         // val cpu = Flipped(Decoupled(new MyCacheDataReq))
    //         /* Write sequentially a whole cacheline */
    //         for (i <- 0 to 7) {
    //             writer.valid.poke(1)
    //             writer.bits.write.poke(1)
    //             writer.bits.wdata.poke(1+i)
    //             writer.bits.addr.poke(i)
    //             c.clock.step(1) 
    //         }
    //         /* Cancel write requests */
    //         writer.valid.poke(0)
    //         writer.bits.write.poke(0)
    //         writer.bits.wdata.poke(11)
    //         writer.bits.addr.poke(2)
    //         c.clock.step(1) 
    //         /* Read and see if I can get correct value */ 
    //         reader.req.bits.addr.poke(2.U)
    //         val readVal = reader.resp.bits.data.peek()
    //         c.clock.step(1) 
    //         println(s"ReadVal $readVal")
            
    //     }

    // }

    
}

