package ECPT.PTW.Debug
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}


class BOOM_PTW_DebugIO(implicit p : Parameters) extends CoreBundle()(p) {
  val r_req = Output(new PTWReq)
  
}