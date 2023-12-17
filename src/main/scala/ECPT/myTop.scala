package ECPT.TOP

import chisel3._
import freechips.rocketchip.tile._ 
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import scala.collection.mutable.ListBuffer
import boom.lsu.BoomNonBlockingDCache
import freechips.rocketchip.diplomacy._
import ECPT.PTW._
import ECPT.Debug._
import ECPT.DummyPeriphrals._


class myTop (implicit p : Parameters) extends CoreModule()(p) {
    val io = IO(new Bundle{
        val debug = new PTW_DebugIO
    })
    val ptw  = Module(new BOOM_PTW(1)(p))
    // ptw.io.requestor := DontCare
    ptw.io.mem := DontCare
    // ptw.io.dpath := DontCare
    val dummyCSR = Module(new DummyCSR()(p))
    val dummyTLB = Module(new DummyTLB()(p))
    val staticMetaId = 0
    val dcache = Module(LazyModule(new BoomNonBlockingDCache(staticMetaId)).module)
    val ptwPorts = ListBuffer(dummyTLB.io.ptw)
    ptw.io.dpath <> dummyCSR.io.dpath
    ptw.io.requestor <> ptwPorts.toSeq
    // dcache.io.lsu <> ptw.io.mem

    // val hellaCachePorts  = ListBuffer[HellaCacheIO]()
    // hellaCachePorts += ptw.io.mem
    // val hellaCacheArb = Module(new HellaCacheArbiter(hellaCachePorts.length)(p))
    // hellaCacheArb.io.requestor <> hellaCachePorts.toSeq
    // // hellaCacheArb.io.mem <>
    
    

}



