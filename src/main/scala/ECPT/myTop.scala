package ECPT.PTW.TOP

import chisel3._
import freechips.rocketchip.tile._ 
import freechips.rocketchip.config.Parameters
import ECPT.PTW._
import freechips.rocketchip.rocket._
import scala.collection.mutable.ListBuffer


class myTop (implicit p : Parameters) extends CoreModule()(p) {
    val ptw  = Module(new BOOM_PTW(1)(p))
    val hellaCachePorts  = ListBuffer[HellaCacheIO]()
    hellaCachePorts += ptw.io.mem
    val hellaCacheArb = Module(new HellaCacheArbiter(hellaCachePorts.length)(p))
    hellaCacheArb.io.requestor <> hellaCachePorts.toSeq
    // hellaCacheArb.io.mem <>

    // lazy val dcache: BoomNonBlockingDCache = LazyModule(new BoomNonBlockingDCache(staticIdForMetadataUseOnly))
    // val dcache = new BoomNonBlockingDCache(staticIdForMetadataUseOnly
}



