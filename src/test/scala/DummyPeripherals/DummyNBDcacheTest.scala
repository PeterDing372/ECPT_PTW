package ECPT.DummyPeriphrals

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import ECPT.Params._
import chipsalliance.rocketchip.config._
import ECPT_Test.BoomTestUtils
import DummyPeripherals.DummyNBDcache
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import chisel3.util.UIntToOH

class DummyNBDcacheSpec extends AnyFreeSpec with ChiselScalatestTester with MemoryOpConstants {
    
  val boomParams: Parameters = BoomTestUtils.getParameters("BoomConfigForTest")
    implicit val para: Parameters = boomParams



  "DummyNBDcache should store and read data" in {
    test(new DummyNBDcache()) { c =>
      val writer = c.io.req
      val reader = c.io.resp

      // Example write to cache
      writer.valid.poke(true.B)
      writer.bits.addr.poke(0.U)
      writer.bits.cmd.poke(M_XWR) // Write command
      writer.bits.data.poke(42.U) // Arbitrary data
      // writer.bits.mask.poke(~0.U(1.W)) // Arbitrary data
      
      c.clock.step(1)

      // Stop writing
      writer.valid.poke(false.B)
      c.clock.step(1)

      // Example read from cache
      writer.valid.poke(true.B)
      writer.bits.addr.poke(0.U)
      writer.bits.cmd.poke(M_XRD) // Read command
      c.clock.step(1)

      // Check read data
      // assert(reader.bits.data.peek().litValue == 42)
      reader.bits.data.expect(42.U)
      c.clock.step(1)

      // write a different value
      writer.valid.poke(true.B)
      writer.bits.addr.poke(0.U)
      writer.bits.cmd.poke(M_XWR) // Write command
      writer.bits.data.poke(41.U) // Arbitrary data
      c.clock.step(1)
      // Stop writing
      writer.valid.poke(false.B)
      c.clock.step(1)
      // Example read from cache
      writer.valid.poke(true.B)
      writer.bits.addr.poke(0.U)
      writer.bits.cmd.poke(M_XRD) // Read command
      c.clock.step(1)
      reader.bits.data.expect(41.U)
      c.clock.step(1)
    }
  }

//   it should "correctly store data in the tag array" in {
//     test(new SimplifiedCache()) { c =>
//       // Similar to the previous test, but you need to check the tag array
//       // Note: Since the tag array is internal, you may need to expose it for testing
//       // or infer its behavior based on cache hits/misses
//     }
//   }

//   it should "correctly read previous write data and tags" in {
//     test(new SimplifiedCache()) { c =>
//       // This test can combine the aspects of the first two tests
//       // You write to the cache, then read back and check both data and tags
//       // Ensure that the read data and tag match what was written
//     }
//   }
}
