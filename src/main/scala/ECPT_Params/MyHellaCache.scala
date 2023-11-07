package ECPT.Params

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import chipsalliance.rocketchip.config._

import freechips.rocketchip.util._
import freechips.rocketchip.rocket._




abstract class MyL1HellaCacheModule(implicit val p: Parameters) extends Module
  with MyHasL1HellaCacheParameters

abstract class MyL1HellaCacheBundle(implicit val p: Parameters) extends ParameterizedBundle()(p)
  with MyHasL1HellaCacheParameters

trait MyHasCoreData extends MyHasCoreParameters {
  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class MyHellaCacheReqInternal(implicit p: Parameters) extends MyCoreBundle()(p) with MyHasCoreMemOp {
  val phys = Bool()
  val no_alloc = Bool()
  val no_xcpt = Bool()
}

trait MyHasCoreMemOp extends MyHasL1HellaCacheParameters {
  val addr = UInt(coreMaxAddrBits.W)
  val idx  = (usingVM && untagBits > pgIdxBits).option(UInt(coreMaxAddrBits.W))
  val tag  = UInt((coreParams.dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val cmd  = UInt(M_SZ.W)
  val size = UInt(log2Ceil(coreDataBytes.log2 + 1).W)
  val signed = Bool()
  val dprv = UInt(PRV.SZ.W)
  val dv = Bool()
}

class MyHellaCacheReq(implicit p: Parameters) extends MyHellaCacheReqInternal()(p) with MyHasCoreData

// interface between D$ and processor/DTLB
class MyHellaCacheIO(implicit p: Parameters) extends MyCoreBundle()(p) {
  val req = Decoupled(new MyHellaCacheReq)
  val s1_kill = Output(Bool()) // kill previous cycle's req
  val s1_data = Output(new MyHellaCacheWriteData()) // data for previous cycle's req
  val s2_nack = Input(Bool()) // req from two cycles ago is rejected
  val s2_nack_cause_raw = Input(Bool()) // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill = Output(Bool()) // kill req from two cycles ago
  val s2_uncached = Input(Bool()) // advisory signal that the access is MMIO
  val s2_paddr = Input(UInt(paddrBits.W)) // translated address

  val resp = Flipped(Valid(new MyHellaCacheResp))
  val replay_next = Input(Bool())
  val s2_xcpt = Input(new HellaCacheExceptions)
  val s2_gpa = Input(UInt(vaddrBitsExtended.W))
  val s2_gpa_is_pte = Input(Bool())
  val uncached_resp = tileParams.dcache.get.separateUncachedResp.option(Flipped(Decoupled(new MyHellaCacheResp)))
  val ordered = Input(Bool())
  val perf = Input(new HellaCachePerfEvents())

  val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
  val clock_enabled = Input(Bool()) // is D$ currently being clocked?
}

trait MyHasL1HellaCacheParameters extends MyHasL1CacheParameters with MyHasCoreParameters {
  val cacheParams = tileParams.dcache.get
  val cfg = cacheParams

  def wordBits = coreDataBits
  def wordBytes = coreDataBytes
  def subWordBits = cacheParams.subWordBits.getOrElse(wordBits)
  def subWordBytes = subWordBits / 8
  def wordOffBits = log2Up(wordBytes)
  def beatBytes = cacheBlockBytes / cacheDataBeats
  def beatWords = beatBytes / wordBytes
  def beatOffBits = log2Up(beatBytes)
  def idxMSB = untagBits-1
  def idxLSB = blockOffBits
  def offsetmsb = idxLSB-1
  def offsetlsb = wordOffBits
  def rowWords = rowBits/wordBits
  def doNarrowRead = coreDataBits * nWays % rowBits == 0
  def eccBytes = cacheParams.dataECCBytes // 1
  val eccBits = cacheParams.dataECCBytes * 8 // 8
  val encBits = cacheParams.dataCode.width(eccBits) // 8
  val encWordBits = encBits * (wordBits / eccBits)
  def encDataBits = cacheParams.dataCode.width(coreDataBits) // NBDCache only
  def encRowBits = encDataBits*rowWords
  def lrscCycles = coreParams.lrscCycles // ISA requires 16-insn LRSC sequences to succeed
  def lrscBackoff = 3 // disallow LRSC reacquisition briefly
  def blockProbeAfterGrantCycles = 8 // give the processor some time to issue a request after a grant
  def nIOMSHRs = cacheParams.nMMIOs
  def maxUncachedInFlight = cacheParams.nMMIOs
  def dataScratchpadSize = cacheParams.dataScratchpadBytes

  require(rowBits >= coreDataBits, s"rowBits($rowBits) < coreDataBits($coreDataBits)")
  if (!usingDataScratchpad)
    require(rowBits == cacheDataBits, s"rowBits($rowBits) != cacheDataBits($cacheDataBits)")
  // would need offset addr for puts if data width < xlen
  require(xLen <= cacheDataBits, s"xLen($xLen) > cacheDataBits($cacheDataBits)")
}

class MyHellaCacheResp(implicit p: Parameters) extends MyCoreBundle()(p)
    with MyHasCoreMemOp
    with MyHasCoreData {
  val replay = Bool()
  val has_data = Bool()
  val data_word_bypass = UInt(coreDataBits.W)
  val data_raw = UInt(coreDataBits.W)
  val store_data = UInt(coreDataBits.W)
}

class MyHellaCacheWriteData(implicit p: Parameters) extends MyCoreBundle()(p) with MyHasCoreData

case class MyDCacheParams(
    nSets: Int = 64,
    nWays: Int = 4,
    rowBits: Int = 64,
    subWordBits: Option[Int] = None,
    replacementPolicy: String = "random",
    nTLBSets: Int = 1,
    nTLBWays: Int = 32,
    nTLBBasePageSectors: Int = 4,
    nTLBSuperpages: Int = 4,
    tagECC: Option[String] = None,
    dataECC: Option[String] = None,
    dataECCBytes: Int = 1,
    nMSHRs: Int = 1,
    nSDQ: Int = 17,
    nRPQ: Int = 16,
    nMMIOs: Int = 1,
    blockBytes: Int = 64,
    separateUncachedResp: Boolean = false,
    acquireBeforeRelease: Boolean = false,
    pipelineWayMux: Boolean = false,
    clockGate: Boolean = false,
    scratch: Option[BigInt] = None) extends L1CacheParams {

  def tagCode: Code = Code.fromString(tagECC)
  def dataCode: Code = Code.fromString(dataECC)

  def dataScratchpadBytes: Int = scratch.map(_ => nSets*blockBytes).getOrElse(0)

  def replacement = new RandomReplacement(nWays)

  def silentDrop: Boolean = !acquireBeforeRelease

  require((!scratch.isDefined || nWays == 1),
    "Scratchpad only allowed in direct-mapped cache.")
  require((!scratch.isDefined || nMSHRs == 0),
    "Scratchpad only allowed in blocking cache.")
  if (scratch.isEmpty)
    require(isPow2(nSets), s"nSets($nSets) must be pow2")
}
