package ECPT.Params

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._

class MySFenceReq(implicit p: Parameters) extends MyCoreBundle()(p) {
  val rs1 = Bool()
  val rs2 = Bool()
  val addr = UInt(vaddrBits.W)
  val asid = UInt((asIdBits max 1).W) // TODO zero-width
  val hv = Bool()
  val hg = Bool()
}