package ECPT.PTW.TOP

import chisel3._
import freechips.rocketchip.tile._ 
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import scala.collection.mutable.ListBuffer
import boom.lsu.BoomNonBlockingDCache
import freechips.rocketchip.diplomacy._
import ECPT.PTW._
import ECPT.PTW.Debug._
import ECPT.DummmyPeriphrals._


class myTop (implicit p : Parameters) extends CoreModule()(p) {
    val io = IO(new Bundle{
        val debug = new BOOM_PTW_DebugIO
    })
    val ptw  = Module(new BOOM_PTW(1)(p))
    ptw.io.requestor := DontCare
    ptw.io.mem := DontCare
    // ptw.io.dpath := DontCare
    val dummyCSR = Module(new DummyCSR()(p))
    ptw.io.dpath <> dummyCSR.io.dpath

    // val hellaCachePorts  = ListBuffer[HellaCacheIO]()
    // hellaCachePorts += ptw.io.mem
    // val hellaCacheArb = Module(new HellaCacheArbiter(hellaCachePorts.length)(p))
    // hellaCacheArb.io.requestor <> hellaCachePorts.toSeq
    // // hellaCacheArb.io.mem <>

    // lazy val dcache: BoomNonBlockingDCache = LazyModule(new BoomNonBlockingDCache(staticIdForMetadataUseOnly))
    // val dcache = new BoomNonBlockingDCache(staticIdForMetadataUseOnly
}



