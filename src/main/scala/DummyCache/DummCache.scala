package ECPT.DummmyCache

import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import chisel3.withClock
import chisel3.internal.sourceinfo.SourceInfo
import chipsalliance.rocketchip.config._
// import ECPT.PTW._
import ECPT.Params._



/* Dummmy Cache module for response */
class DummmyCache (implicit p :  Parameters) extends MyL1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val ptw = Flipped(new MyHellaCacheIO) // input
    val cpu = Flipped(Decoupled(new MyCacheDataReq))
  })

  val MyIdxBits = 3

  def addrToIdx(addr: UInt) : UInt = addr(MyIdxBits-1, blockOffBits)
  def addrToOffset(addr: UInt) : UInt = addr(blockOffBits, 0) 

  val request = Decoupled(Wire(new MyHellaCacheReq))
  request := io.ptw.req


  val cacheMem = Mem(8, new MyCacheLine) // declare cache data array

  val idx = addrToIdx(io.cpu.bits.addr)  // Extract index from address
  val newCacheLine = Wire(new MyCacheLine)
  val oldCacheLine = cacheMem.read(idx) // io.cpu.valid && io.cpu.bits.write


   // On write, update the cache line with new data at the specific offset
  when(io.cpu.valid && io.cpu.bits.write) {
    val offset = addrToOffset(io.cpu.bits.addr)  // Extract offset from address
    val wmask = Wire(Vec(8, Bool()))

    // Only the entry corresponding to the offset should be updated
    wmask := VecInit(Seq.fill(8){false.B})
    wmask(offset) := true.B

    // Update the cache line data at the specified offset, preserve other entries
    for (i <- 0 until 8) {
      when(wmask(i)) {
        newCacheLine.data(i) := io.cpu.bits.wdata
      } .otherwise {
        newCacheLine.data(i) := oldCacheLine.data(i)
      }
    }
  }

  newCacheLine.valid := true.B  // Set the valid bit to true
  newCacheLine.tag := io.cpu.bits.addr(pgUntagBits + tagBits, pgUntagBits) // Set the tag to the provided address

  // Perform the write to the cache memory
  cacheMem.write(idx, newCacheLine)

} 

/* This is a CacheLine with one one entry: TODO change this to be 64 byts */
class MyCacheLine (implicit p : Parameters) extends MyL1HellaCacheBundle()(p) {
  val valid = Bool()
  val tag = UInt(tagBits.W)
  val data = Vec(8, UInt(coreDataBits.W))

} 

class MyCacheDataReq(implicit p : Parameters) extends MyL1HellaCacheBundle()(p) {
  val write = Bool()
  val addr = UInt(vaddrBitsExtended.W)
  val idx = UInt(idxBits.W)
  val wdata = UInt(coreDataBits.W)
}