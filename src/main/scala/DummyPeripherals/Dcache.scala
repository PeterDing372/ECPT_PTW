package DummyPeripherals

//******************************************************************************
// Ported from Rocket-Chip
// See LICENSE.Berkeley and LICENSE.SiFive in Rocket-Chip for license details.
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

/******************************************************************
 * WARNING: THIS MODULE IS NOT USABLE. PLEASE DO NOT USE!
 * This module contains critical issues or is under development.
 * Using this module can lead to unexpected behavior or system failure.
 ******************************************************************/


import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._

import boom.common._
import boom.exu.BrUpdateInfo
import boom.util.{IsKilledByBranch, GetNewBrMask, BranchKillableQueue, IsOlder, UpdateBrMask, AgePriorityEncoder, WrapInc, Transpose}
import boom.lsu._




class BoomDuplicatedDataArray(implicit p: Parameters) extends AbstractBoomDataArray
{

  val waddr = io.write.bits.addr >> rowOffBits
  for (j <- 0 until memWidth) {

    val raddr = io.read(j).bits.addr >> rowOffBits
    for (w <- 0 until nWays) {
      val array = DescribedSRAM(
        name = s"array_${w}_${j}",
        desc = "Non-blocking DCache Data Array",
        size = nSets * refillCycles,
        data = Vec(rowWords, Bits(encDataBits.W))
      )
      when (io.write.bits.way_en(w) && io.write.valid) {
        val data = VecInit((0 until rowWords) map (i => io.write.bits.data(encDataBits*(i+1)-1,encDataBits*i)))
        array.write(waddr, data, io.write.bits.wmask.asBools)
      }
      io.resp(j)(w) := RegNext(array.read(raddr, io.read(j).bits.way_en(w) && io.read(j).valid).asUInt)
    }
    io.nacks(j) := false.B
  }
}

class BoomBankedDataArray(implicit p: Parameters) extends AbstractBoomDataArray {

  val nBanks   = boomParams.numDCacheBanks
  val bankSize = nSets * refillCycles / nBanks
  require (nBanks >= memWidth)
  require (bankSize > 0)

  val bankBits    = log2Ceil(nBanks)
  val bankOffBits = log2Ceil(rowWords) + log2Ceil(wordBytes)
  val bidxBits    = log2Ceil(bankSize)
  val bidxOffBits = bankOffBits + bankBits

  //----------------------------------------------------------------------------------------------------

  val s0_rbanks = if (nBanks > 1) VecInit(io.read.map(r => (r.bits.addr >> bankOffBits)(bankBits-1,0))) else VecInit(0.U)
  val s0_wbank  = if (nBanks > 1) (io.write.bits.addr >> bankOffBits)(bankBits-1,0) else 0.U
  val s0_ridxs  = VecInit(io.read.map(r => (r.bits.addr >> bidxOffBits)(bidxBits-1,0)))
  val s0_widx   = (io.write.bits.addr >> bidxOffBits)(bidxBits-1,0)

  val s0_read_valids    = VecInit(io.read.map(_.valid))
  val s0_bank_conflicts = pipeMap(w => (0 until w).foldLeft(false.B)((c,i) => c || io.read(i).valid && s0_rbanks(i) === s0_rbanks(w)))
  val s0_do_bank_read   = s0_read_valids zip s0_bank_conflicts map {case (v,c) => v && !c}
  val s0_bank_read_gnts = Transpose(VecInit(s0_rbanks zip s0_do_bank_read map {case (b,d) => VecInit((UIntToOH(b) & Fill(nBanks,d)).asBools)}))
  val s0_bank_write_gnt = (UIntToOH(s0_wbank) & Fill(nBanks, io.write.valid)).asBools

  //----------------------------------------------------------------------------------------------------

  val s1_rbanks         = RegNext(s0_rbanks)
  val s1_ridxs          = RegNext(s0_ridxs)
  val s1_read_valids    = RegNext(s0_read_valids)
  val s1_pipe_selection = pipeMap(i => VecInit(PriorityEncoderOH(pipeMap(j =>
                            if (j < i) s1_read_valids(j) && s1_rbanks(j) === s1_rbanks(i)
                            else if (j == i) true.B else false.B))))
  val s1_ridx_match     = pipeMap(i => pipeMap(j => if (j < i) s1_ridxs(j) === s1_ridxs(i)
                                                    else if (j == i) true.B else false.B))
  val s1_nacks          = pipeMap(w => s1_read_valids(w) && (s1_pipe_selection(w).asUInt & ~s1_ridx_match(w).asUInt).orR)
  val s1_bank_selection = pipeMap(w => Mux1H(s1_pipe_selection(w), s1_rbanks))

  //----------------------------------------------------------------------------------------------------

  val s2_bank_selection = RegNext(s1_bank_selection)
  val s2_nacks          = RegNext(s1_nacks)

  for (w <- 0 until nWays) {
    val s2_bank_reads = Reg(Vec(nBanks, Bits(encRowBits.W)))

    for (b <- 0 until nBanks) {
      val array = DescribedSRAM(
        name = s"array_${w}_${b}",
        desc = "Non-blocking DCache Data Array",
        size = bankSize,
        data = Vec(rowWords, Bits(encDataBits.W))
      )
      val ridx = Mux1H(s0_bank_read_gnts(b), s0_ridxs)
      val way_en = Mux1H(s0_bank_read_gnts(b), io.read.map(_.bits.way_en))
      s2_bank_reads(b) := array.read(ridx, way_en(w) && s0_bank_read_gnts(b).reduce(_||_)).asUInt

      when (io.write.bits.way_en(w) && s0_bank_write_gnt(b)) {
        val data = VecInit((0 until rowWords) map (i => io.write.bits.data(encDataBits*(i+1)-1,encDataBits*i)))
        array.write(s0_widx, data, io.write.bits.wmask.asBools)
      }
    }

    for (i <- 0 until memWidth) {
      io.resp(i)(w) := s2_bank_reads(s2_bank_selection(i))
    }
  }

  io.nacks := s2_nacks
}



class MyLSUDMemIO(implicit p: Parameters) extends BoomBundle()(p)
{
  // In LSU's dmem stage, send the request
  val req         = new DecoupledIO(Vec(memWidth, Valid(new BoomDCacheReq)))
  // In LSU's LCAM search stage, kill if order fail (or forwarding possible)
  val s1_kill     = Output(Vec(memWidth, Bool()))
  // Get a request any cycle
  val resp        = Flipped(Vec(memWidth, new ValidIO(new BoomDCacheResp)))
  // In our response stage, if we get a nack, we need to reexecute
  val nack        = Flipped(Vec(memWidth, new ValidIO(new BoomDCacheReq)))

  val brupdate       = Output(new BrUpdateInfo)
  val exception    = Output(Bool())
  val rob_pnr_idx  = Output(UInt(robAddrSz.W))
  val rob_head_idx = Output(UInt(robAddrSz.W))


  // Clears prefetching MSHRs
  val force_order  = Output(Bool())
  val ordered     = Input(Bool())

  val perf = Input(new Bundle {
    val acquire = Bool()
    val release = Bool()
  })

}

class MyBoomDCacheBundle(implicit p: Parameters) extends BoomBundle()(p) {
  val errors = new DCacheErrors
  val lsu   = Flipped(new MyLSUDMemIO)
}


// S0: Send request address
// S1: Access SRAM
// S2: Perform way-select and format response data
class MyBoomNonBlockingDCacheModule(implicit p: Parameters) extends BoomModule()(p)
  with HasL1HellaCacheParameters
  with HasBoomCoreParameters
{
    assert(0.B) // make sure nothing comes through
  val io = IO(new MyBoomDCacheBundle)


  def widthMap[T <: Data](f: Int => T) = VecInit((0 until memWidth).map(f))

  val t_replay :: t_probe :: t_wb :: t_mshr_meta_read :: t_lsu :: t_prefetch :: Nil = Enum(6)


  // tags
  def onReset = L1Metadata(0.U, ClientMetadata.onReset)
  val meta = Seq.fill(memWidth) { Module(new L1MetadataArray(onReset _)) }
  val metaWriteArb = Module(new Arbiter(new L1MetaWriteReq, 2))
  // 0 goes to MSHR refills, 1 goes to prober
  val metaReadArb = Module(new Arbiter(new BoomL1MetaReadReq, 6))
  // 0 goes to MSHR replays, 1 goes to prober, 2 goes to wb, 3 goes to MSHR meta read,
  // 4 goes to pipeline, 5 goes to prefetcher

  metaReadArb.io.in := DontCare
  for (w <- 0 until memWidth) {
    meta(w).io.write.valid := metaWriteArb.io.out.fire
    meta(w).io.write.bits  := metaWriteArb.io.out.bits
    meta(w).io.read.valid  := metaReadArb.io.out.valid
    meta(w).io.read.bits   := metaReadArb.io.out.bits.req(w)
  }
  metaReadArb.io.out.ready  := meta.map(_.io.read.ready).reduce(_||_)
  metaWriteArb.io.out.ready := meta.map(_.io.write.ready).reduce(_||_)

  // data
  val data = Module(if (boomParams.numDCacheBanks == 1) new BoomDuplicatedDataArray else new BoomBankedDataArray)
  val dataWriteArb = Module(new Arbiter(new L1DataWriteReq, 1))
  // 0 goes to pipeline
  val dataReadArb = Module(new Arbiter(new BoomL1DataReadReq, 3))
  // 0 goes to pipeline
  dataReadArb.io.in := DontCare

  for (w <- 0 until memWidth) {
    data.io.read(w).valid := dataReadArb.io.out.bits.valid(w) && dataReadArb.io.out.valid
    data.io.read(w).bits  := dataReadArb.io.out.bits.req(w)
  }
  dataReadArb.io.out.ready := true.B

  data.io.write.valid := dataWriteArb.io.out.fire
  data.io.write.bits  := dataWriteArb.io.out.bits
  dataWriteArb.io.out.ready := true.B

  // ------------
  // New requests

  io.lsu.req.ready := metaReadArb.io.in(4).ready && dataReadArb.io.in(2).ready
  metaReadArb.io.in(4).valid := io.lsu.req.valid
  dataReadArb.io.in(2).valid := io.lsu.req.valid
  for (w <- 0 until memWidth) {
    // Tag read for new requests
    metaReadArb.io.in(4).bits.req(w).idx    := io.lsu.req.bits(w).bits.addr >> blockOffBits
    metaReadArb.io.in(4).bits.req(w).way_en := DontCare
    metaReadArb.io.in(4).bits.req(w).tag    := DontCare
    // Data read for new requests
    dataReadArb.io.in(2).bits.valid(w)      := io.lsu.req.bits(w).valid
    dataReadArb.io.in(2).bits.req(w).addr   := io.lsu.req.bits(w).bits.addr
    dataReadArb.io.in(2).bits.req(w).way_en := ~0.U(nWays.W)
  }



  val s0_valid = Mux(io.lsu.req.fire, VecInit(io.lsu.req.bits.map(_.valid)), VecInit(0.U(memWidth.W).asBools))
  val s0_req   = Mux(io.lsu.req.fire        , VecInit(io.lsu.req.bits.map(_.bits)), VecInit(io.lsu.req.bits.map(_.bits)))
  val s0_type  = Mux(io.lsu.req.fire        , t_lsu, t_replay)

  // Does this request need to send a response or nack
  val s0_send_resp_or_nack = Mux(io.lsu.req.fire, s0_valid, VecInit(0.U(memWidth.W).asBools))



}
