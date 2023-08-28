package testMain

import scala.util._
// import chisel3.iotesters._
import freechips.rocketchip.tile._
import freechips.rocketchip.system._


object RocketChipTest extends App {
    val params = (new DefaultConfig).toInstance
    val crossingParamsval = new RocketCrossingParams
    val dut = new RocketTileParams()
    dut.instantiate();
//   chisel3.Driver.execute(args, () => new Adder(args(0).toInt))
}