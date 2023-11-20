package ECPT.DummmyPeriphrals

import chisel3._
import chisel3.util._
import chisel3.withClock
import chisel3.internal.sourceinfo.SourceInfo
import chipsalliance.rocketchip.config._
// import ECPT.PTW._
import ECPT.Params._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import ECPT.PTW._

class DummyTLB (implicit p : Parameters) extends CoreModule()(p) {
  val io = IO(new Bundle {
    val ptw =  new TLBPTWIO
    // val ptbr =  Output(new PTBR)

  })
  io.ptw.ptbr := DontCare
  io.ptw.hgatp := DontCare
  io.ptw.vsatp := DontCare
  io.ptw.status := DontCare
  io.ptw.hstatus := DontCare
  io.ptw.pmp := DontCare




}
/** IO between TLB and PTW
  *
  * PTW receives :
  *   - PTE request
  *   - CSRs info
  *   - pmp results from PMP(in TLB)
  */
// class TLBPTWIO(implicit p: Parameters) extends CoreBundle()(p)
//     with HasCoreParameters {
//   val req = Decoupled(Valid(new PTWReq))
//   val resp = Flipped(Valid(new PTWResp))
//   val ptbr = Input(new PTBR())
//   val hgatp = Input(new PTBR())
//   val vsatp = Input(new PTBR())
//   val status = Input(new MStatus())
//   val hstatus = Input(new HStatus())
//   val gstatus = Input(new MStatus())
//   val pmp = Input(Vec(nPMPs, new PMP))
//   val customCSRs = Input(coreParams.customCSRs)
// }
