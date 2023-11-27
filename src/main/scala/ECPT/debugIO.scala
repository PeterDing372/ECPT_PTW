package ECPT.Debug
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import ECPT.Params._


/* 
 * This is ports for the ECPT_PTW for us to poke what is happening inside 
 */
class debugPorts_PTW (implicit p : Parameters) extends MyCoreBundle()(p) {
  val debug_state = Output(UInt(3.W))
  val debug_counter = Output(UInt(log2Ceil(8).W))
  val debug_counter_trigger = Output(Bool())
  val req_addr = Output(UInt(vpnBits.W))
  
}


class BOOM_PTW_DebugIO(implicit p : Parameters) extends CoreBundle()(p) {
  val r_req_input = Output(new PTWReq)
  val r_req_arb = Output(new PTWReq)
  val ptwState = Output(UInt(4.W)) // max 15
  val other_logic = new BOOM_PTW_logics
}

class BOOM_PTW_logics (implicit p : Parameters) extends CoreBundle()(p) {
  val vpn = Output(UInt(vpnBits.W))
  val do_both_stages = Output(Bool())
  val pte_addr = (UInt())
}