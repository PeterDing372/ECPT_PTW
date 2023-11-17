package ECPT.PTW.TOP

import chisel3._
import freechips.rocketchip.tile._ 
import freechips.rocketchip.config.Parameters
import ECPT.PTW._


class myTop (implicit p : Parameters) extends CoreModule()(p) {
    val ptw  = Module(new BOOM_PTW(1)(p))
    // lazy val dcache: BoomNonBlockingDCache = LazyModule(new BoomNonBlockingDCache(staticIdForMetadataUseOnly))
    // val dcache = new BoomNonBlockingDCache(staticIdForMetadataUseOnly
}



