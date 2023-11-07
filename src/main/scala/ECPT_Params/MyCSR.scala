package ECPT.Params

import chisel3._
import chipsalliance.rocketchip.config._
import chisel3.util._

import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property

import scala.collection.mutable.LinkedHashMap


class MyPTBR(implicit p: Parameters) extends MyCoreBundle()(p) {
  def additionalPgLevels = mode.extract(log2Ceil(pgLevels-minPgLevels+1)-1, 0)
  def pgLevelsToMode(i: Int) = (xLen, i) match {
    case (32, 2) => 1
    case (64, x) if x >= 3 && x <= 6 => x + 5
  }
  val (modeBits, maxASIdBits) = xLen match {
    case 32 => (1, 9)
    case 64 => (4, 16)
  }
  require(modeBits + maxASIdBits + maxPAddrBits - pgIdxBits == xLen)

  val mode = UInt(modeBits.W)
  val asid = UInt(maxASIdBits.W)
  val ppn = UInt((maxPAddrBits - pgIdxBits).W)
}