package ECPT.PTW

import chisel3._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.rocket._

object PTBR_Init {
  def apply(implicit p: Parameters): PTBR = {
    val instance = new PTBR()(p)
    val modeBits = instance.modeBits
    val maxASIdBits = instance.maxASIdBits
    val maxPAddrBits = instance.maxPAddrBits
    val pgIdxBits = instance.pgIdxBits

    instance.mode := WireInit(0.U(modeBits.W))
    instance.asid := WireInit(0.U(maxASIdBits.W))
    instance.ppn := WireInit(0.U((maxPAddrBits - pgIdxBits).W))
    // instance.mode := Wire(0.U)
    // instance.asid := Wire(0.U)
    // instance.ppn := Wire(0.U)
    
    instance
  }
}

