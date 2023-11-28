package ECPT.DummyPeriphrals

import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import chisel3.withClock
import chisel3.internal.sourceinfo.SourceInfo
import chipsalliance.rocketchip.config._
// import ECPT.PTW._
import ECPT.Params._

// class debugPorts_DummyCache (implicit p : Parameters) extends MyCoreBundle()(p) {
//   val debug_state = Output(UInt(3.W))
//   val debug_counter = Output(UInt(log2Ceil(8).W))
//   val debug_counter_trigger = Output(Bool())
//   val req_addr = Output(UInt(vpnBits.W))
// }


/* Dummmy Cache module for response */
class DummyCache (implicit p :  Parameters) extends MyL1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val ptw = Flipped(new MyHellaCacheIO) // input
    val cpu = Flipped(Decoupled(new MyCacheDataReq))
  })

  /* Mask for subword writes */
  val rmask = VecInit(Seq.fill(8){false.B})
  val wmask = VecInit(Seq.fill(8){false.B})

  val MyIdxBits = 3

  def addrToIdx(addr: UInt) : UInt = addr(MyIdxBits + blockOffBits, blockOffBits)
  def addrToOffset(addr: UInt) : UInt = addr(blockOffBits, 0) 

  // val request = io.ptw.req // TODO

  val cacheData = Mem(8, Vec(8, UInt(coreDataBits.W))) // declare cache data array
  val cacheTag = Mem(8, UInt(tagBits.W))
  /* Connection directly to cacheMem */
  val idx = Wire(UInt(MyIdxBits.W))
  val BlockOffeset = Wire(UInt(blockOffBits.W))

  when (io.cpu.valid && io.cpu.bits.write) {
    idx := addrToIdx(io.cpu.bits.addr)  // Extract index from address
    BlockOffeset := addrToOffset(io.cpu.bits.addr)
  } .otherwise {
    idx := addrToIdx(io.ptw.req.bits.addr)
    BlockOffeset := addrToOffset(io.ptw.req.bits.addr)
  }
  val readCacheline = Wire(Vec(8, UInt(coreDataBits.W)))
  val write_data = Wire(Vec(8, UInt(coreDataBits.W)))
  readCacheline := cacheData.read(idx)

  /* On read mask the cacheline and return word block */
  io.ptw.resp.bits.data :=  readCacheline(BlockOffeset)

  // On write, update the cache line with new data at the specific offset
  when(io.cpu.valid && io.cpu.bits.write) {
    // val wmask = VecInit(Seq.fill(8){false.B})
    // Only the entry corresponding to the offset should be updated
    wmask(BlockOffeset) := true.B
    /* Correctly assign the write data */
    for (i <- 0 until 8) {
      when(wmask(i)) {
        write_data(i) := io.cpu.bits.wdata
      } .otherwise {
        write_data(i) := readCacheline(i)
      }
    }
    cacheData.write(idx, write_data, wmask)
  } .otherwise {
    write_data := VecInit(Seq.fill(8)(0.U(coreDataBits.W)))
  }
  
  
  /* Debug section */
  if(debug_flag) {
    printf("-------- [DUMMYCACHE] DEBUG INFO START --------\n")
    printf("readCacheline: %d wmask: %d \n", 
          readCacheline(BlockOffeset), wmask(BlockOffeset))
    printf("idx: %d offset: %d \n", 
          idx, BlockOffeset)
    printf("write cpu valid: %d write_en: %d \n", io.cpu.valid, io.cpu.bits.write)
          
    printf("-------- [DUMMYCACHE] DEBUG INFO END --------\n")
  }

  
  /* Might be useful */
  io.ptw.resp.valid := true.B
  io.ptw.req.ready := true.B

  /* CPU */
  io.cpu.ready := true.B


  /* Initialize all unrealated io */
  // io.ptw.s1_kill := false.B
  // io.ptw.s1_data := 0.U 
  /* Output by cache module */
  // io.ptw.s2_nack := false.B
  // io.ptw.s2_nack_cause_raw := false.B
  // io.ptw.s2_uncached := false.B
  // io.ptw.s2_paddr := 0.U 
  // io.ptw.replay_next := false.B
  // io.ptw.s2_xcpt := false.B
  // io.ptw.s2_gpa := 0.U 
  // io.ptw.s2_gpa_is_pte := false.B
  // io.ptw.ordered := false.B
  // io.ptw.perf := 0.U 
  // io.ptw.clock_enabled := false.B


  // io.ptw.s2_kill := 0.U
  // io.ptw.resp := 0.U 
  // io.ptw.uncached_resp := 0.U 
  // io.ptw.keep_clock_enabled := 0.U 

  io.ptw.resp.bits.signed := false.B
  io.ptw.resp.bits.dv := false.B
  io.ptw.resp.bits.dprv := 0.U
  io.ptw.resp.bits.addr := 0.U
  io.ptw.resp.bits.tag := 0.U
  io.ptw.resp.bits.store_data := 0.U
  io.ptw.resp.bits.replay := false.B
  io.ptw.resp.bits.data_word_bypass := 0.U
  io.ptw.resp.bits.size := 0.U
  io.ptw.resp.bits.cmd := 0.U
  io.ptw.resp.bits.data_raw := 0.U
  io.ptw.resp.bits.has_data := 0.U
  io.ptw.resp.bits.mask := 0.U

}

class DummyIO

/* This is a CacheLine with one one entry: TODO change this to be 64 byts */
class MyCacheLine (implicit p : Parameters) extends MyL1HellaCacheBundle()(p) {
  val valid = Bool()
  val tag = UInt(tagBits.W)
  println(s"tagBits: $tagBits")
  val data = Vec(8, UInt(coreDataBits.W))

} 

class MyCacheDataReq(implicit p : Parameters) extends MyL1HellaCacheBundle()(p) {
  val write = Bool()
  val addr = UInt(vaddrBitsExtended.W)
  // println(s"vaddrBitsExtended: $vaddrBitsExtended")
  // val idx = UInt(idxBits.W)
  val wdata = UInt(coreDataBits.W)
}