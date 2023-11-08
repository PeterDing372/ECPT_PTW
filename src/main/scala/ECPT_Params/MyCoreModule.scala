package ECPT.Params

// import chisel3._
// import chisel3.util._
import Chisel._
import freechips.rocketchip.tile._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._





abstract class MyCoreModule(implicit val p: Parameters) extends Module
  with MyHasCoreParameters with HasDebugFlag

trait MyHasCoreParameters extends MyHasTileParameters {

  val coreParams: CoreParams = tileParams.core

  val minFLen = coreParams.fpu.map(_ => coreParams.minFLen).getOrElse(0)
  val fLen = coreParams.fpu.map(_.fLen).getOrElse(0)

  val usingMulDiv = coreParams.mulDiv.nonEmpty
  val usingFPU = coreParams.fpu.nonEmpty
  val usingAtomics = coreParams.useAtomics
  val usingAtomicsOnlyForIO = coreParams.useAtomicsOnlyForIO
  val usingAtomicsInCache = usingAtomics && !usingAtomicsOnlyForIO
  val usingCompressed = coreParams.useCompressed
  val usingBitManip = coreParams.useBitManip
  // val usingBitManipCrypto = coreParams.hasBitManipCrypto
  val usingVector = coreParams.useVector
  val usingSCIE = coreParams.useSCIE
  // val usingCryptoNIST = coreParams.useCryptoNIST
  // val usingCryptoSM = coreParams.useCryptoSM
  val usingNMI = coreParams.useNMI

  val retireWidth = coreParams.retireWidth
  val fetchWidth = coreParams.fetchWidth
  val decodeWidth = coreParams.decodeWidth

  val fetchBytes = coreParams.fetchBytes
  val coreInstBits = coreParams.instBits
  val coreInstBytes = coreInstBits/8
  val coreDataBits = xLen max fLen max vMemDataBits
  val coreDataBytes = coreDataBits/8
  def coreMaxAddrBits = paddrBits max vaddrBitsExtended

  val nBreakpoints = coreParams.nBreakpoints
  val nPMPs = coreParams.nPMPs
  val pmpGranularity = coreParams.pmpGranularity
  val nPerfCounters = coreParams.nPerfCounters
  val mtvecInit = coreParams.mtvecInit
  val mtvecWritable = coreParams.mtvecWritable
  // val customIsaExt = coreParams.customIsaExt
  // val traceHasWdata = coreParams.traceHasWdata

  def vLen = coreParams.vLen
  def sLen = coreParams.sLen
  def eLen = coreParams.eLen(xLen, fLen)
  def vMemDataBits = if (usingVector) coreParams.vMemDataBits else 0
  def maxVLMax = vLen

  if (usingVector) {
    require(isPow2(vLen), s"vLen ($vLen) must be a power of 2")
    require(eLen >= 32 && vLen % eLen == 0, s"eLen must divide vLen ($vLen) and be no less than 32")
    require(vMemDataBits >= eLen && vLen % vMemDataBits == 0, s"vMemDataBits ($vMemDataBits) must divide vLen ($vLen) and be no less than eLen ($eLen)")
  }

  lazy val hartIdLen: Int = p(MaxHartIdBits)
  lazy val resetVectorLen: Int = {
    val externalLen = paddrBits
    require(externalLen <= xLen, s"External reset vector length ($externalLen) must be <= XLEN ($xLen)")
    require(externalLen <= vaddrBitsExtended, s"External reset vector length ($externalLen) must be <= virtual address bit width ($vaddrBitsExtended)")
    externalLen
  }

  // Print out log of committed instructions and their writeback values.
  // Requires post-processing due to out-of-order writebacks.
  val enableCommitLog = false

}



trait MyHasTileParameters extends MyHasNonDiplomaticTileParameters {

  lazy val paddrBits: Int = 32
  /* These are used to check/debug the require statement */
  // val test_a = maxHVAddrBits
  // val test_b = xLen
  // println((s"\n\nThe maxHVAddrBits: ${test_a}\n xLen: ${test_b}\n\n\n\n\n\n\n\n"))

  def vaddrBits: Int =
    if (usingVM) {
      val v = maxHVAddrBits
      require(v == xLen || xLen > v && v > paddrBits)
      v
    } else {
      // since virtual addresses sign-extend but physical addresses
      // zero-extend, make room for a zero sign bit for physical addresses
      (paddrBits + 1) min xLen
    }
  def vpnBits: Int = vaddrBits - pgIdxBits
  def ppnBits: Int = paddrBits - pgIdxBits
  def vpnBitsExtended: Int = vpnBits + (vaddrBits < xLen).toInt // 28
  def vaddrBitsExtended: Int = vpnBitsExtended + pgIdxBits // 27 + 12 = 39
}

// /** These parameters values are not computed based on diplomacy negotiation
//   * and so are safe to use while diplomacy itself is running.
//   */
trait MyHasNonDiplomaticTileParameters {
  implicit val p: Parameters
  implicit class BooleanOps(b: Boolean) {
    def toInt: Int = if (b) 1 else 0
    }
  def tileParams: MyTileParams = MyTileParams()

  def usingVM: Boolean = tileParams.core.useVM
  def usingUser: Boolean = tileParams.core.useUser || usingSupervisor
  def usingSupervisor: Boolean = tileParams.core.hasSupervisorMode
  def usingHypervisor: Boolean = usingVM && tileParams.core.useHypervisor
  def usingDebug: Boolean = tileParams.core.useDebug
  def usingRoCC: Boolean = !p(BuildRoCC).isEmpty
  def usingBTB: Boolean = tileParams.btb.isDefined && tileParams.btb.get.nEntries > 0
  def usingPTW: Boolean = usingVM
  def usingDataScratchpad: Boolean = tileParams.dcache.flatMap(_.scratch).isDefined

  def xLen: Int = p(XLen)
  def xBytes: Int = xLen / 8
  def iLen: Int = 32
  def pgIdxBits: Int = 12
  def pgLevelBits: Int = 10 - log2Ceil(xLen / 32)
  def pgLevels: Int = p(PgLevels)
  def maxSVAddrBits: Int = pgIdxBits + pgLevels * pgLevelBits
  def maxHypervisorExtraAddrBits: Int = 2
  def hypervisorExtraAddrBits: Int = {
    if (usingHypervisor) maxHypervisorExtraAddrBits
    else 0
  }
  def maxHVAddrBits: Int = maxSVAddrBits + hypervisorExtraAddrBits
  def minPgLevels: Int = {
    val res = xLen match { case 32 => 2; case 64 => 3 }
    require(pgLevels >= res)
    res
  }
  def asIdBits: Int = p(ASIdBits)
  def vmIdBits: Int = p(VMIdBits)
  lazy val maxPAddrBits: Int = {
    require(xLen == 32 || xLen == 64, s"Only XLENs of 32 or 64 are supported, but got $xLen")
    xLen match { case 32 => 34; case 64 => 56 }
  }

  /** Use staticIdForMetadataUseOnly to emit information during the build or identify a component to diplomacy.
    *
    *   Including it in a constructed Chisel circuit by converting it to a UInt will prevent
    *   Chisel/FIRRTL from being able to deduplicate tiles that are otherwise homogeneous,
    *   a property which is important for hierarchical place & route flows.
    */
  def staticIdForMetadataUseOnly: Int = tileParams.hartId
  @deprecated("use hartIdSinkNodeOpt.map(_.bundle) or staticIdForMetadataUseOnly", "rocket-chip 1.3")
  def hartId: Int = staticIdForMetadataUseOnly

  def cacheBlockBytes = p(CacheBlockBytes)
  def lgCacheBlockBytes = log2Up(cacheBlockBytes)
  def masterPortBeatBytes = p(SystemBusKey).beatBytes

  // TODO make HellaCacheIO diplomatic and remove this brittle collection of hacks
  //                  Core   PTW                DTIM                    coprocessors           
  def dcacheArbPorts = 1 + usingVM.toInt + usingDataScratchpad.toInt + p(BuildRoCC).size + tileParams.core.useVector.toInt

  // TODO merge with isaString in CSR.scala
  // def isaDTS: String = {
  //   val ie = if (tileParams.core.useRVE) "e" else "i"
  //   val m = if (tileParams.core.mulDiv.nonEmpty) "m" else ""
  //   val a = if (tileParams.core.useAtomics) "a" else ""
  //   val f = if (tileParams.core.fpu.nonEmpty) "f" else ""
  //   val d = if (tileParams.core.fpu.nonEmpty && tileParams.core.fpu.get.fLen > 32) "d" else ""
  //   val c = if (tileParams.core.useCompressed) "c" else ""
  //   val v = if (tileParams.core.useVector) "v" else ""
  //   val h = if (usingHypervisor) "h" else ""
  //   // val multiLetterExt = (
  //   //   // rdcycle[h], rdinstret[h] is implemented
  //   //   // rdtime[h] is not implemented, and could be provided by software emulation
  //   //   // see https://github.com/chipsalliance/rocket-chip/issues/3207
  //   //   //Some(Seq("Zicntr")) ++
  //   //   Some(Seq("Zicsr", "Zifencei", "Zihpm")) ++
  //   //   Option.when(tileParams.core.fpu.nonEmpty && tileParams.core.fpu.get.fLen >= 16 && tileParams.core.fpu.get.minFLen <= 16)(Seq("Zfh")) ++
  //   //   Option.when(tileParams.core.useBitManip)(Seq("Zba", "Zbb", "Zbc")) ++
  //   //   Option.when(tileParams.core.hasBitManipCrypto)(Seq("Zbkb", "Zbkc", "Zbkx")) ++
  //   //   Option.when(tileParams.core.useBitManip)(Seq("Zbs")) ++
  //   //   Option.when(tileParams.core.useCryptoNIST)(Seq("Zknd", "Zkne", "Zknh")) ++
  //   //   Option.when(tileParams.core.useCryptoSM)(Seq("Zksed", "Zksh")) ++
  //   //   tileParams.core.customIsaExt.map(Seq(_))
  //   // ).flatten
  //   // val multiLetterString = multiLetterExt.mkString("_")
  // //   s"rv${p(XLen)}$ie$m$a$f$d$c$v$h$multiLetterString"
  // }

  def tileProperties: PropertyMap = {
    val dcache = tileParams.dcache.filter(!_.scratch.isDefined).map(d => Map(
      "d-cache-block-size"   -> cacheBlockBytes.asProperty,
      "d-cache-sets"         -> d.nSets.asProperty,
      "d-cache-size"         -> (d.nSets * d.nWays * cacheBlockBytes).asProperty)
    ).getOrElse(Nil)

    val incoherent = if (!tileParams.core.useAtomicsOnlyForIO) Nil else Map(
      "sifive,d-cache-incoherent" -> Nil)

    val icache = tileParams.icache.map(i => Map(
      "i-cache-block-size"   -> cacheBlockBytes.asProperty,
      "i-cache-sets"         -> i.nSets.asProperty,
      "i-cache-size"         -> (i.nSets * i.nWays * cacheBlockBytes).asProperty)
    ).getOrElse(Nil)

    val dtlb = tileParams.dcache.filter(_ => tileParams.core.useVM).map(d => Map(
      "d-tlb-size"           -> (d.nTLBWays * d.nTLBSets).asProperty,
      "d-tlb-sets"           -> d.nTLBSets.asProperty)).getOrElse(Nil)

    val itlb = tileParams.icache.filter(_ => tileParams.core.useVM).map(i => Map(
      "i-tlb-size"           -> (i.nTLBWays * i.nTLBSets).asProperty,
      "i-tlb-sets"           -> i.nTLBSets.asProperty)).getOrElse(Nil)

    val mmu =
      if (tileParams.core.useVM) {
        if (tileParams.core.useHypervisor) {
          Map("tlb-split" -> Nil, "mmu-type" -> s"riscv,sv${maxSVAddrBits},sv${maxSVAddrBits}x4".asProperty)
        } else {
          Map("tlb-split" -> Nil, "mmu-type" -> s"riscv,sv$maxSVAddrBits".asProperty)
        }
      } else {
        Nil
      }

    val pmp = if (tileParams.core.nPMPs > 0) Map(
      "riscv,pmpregions" -> tileParams.core.nPMPs.asProperty,
      "riscv,pmpgranularity" -> tileParams.core.pmpGranularity.asProperty) else Nil

    dcache ++ icache ++ dtlb ++ itlb ++ mmu ++ pmp ++ incoherent
  }

}

case class MyTileParams(
    core: RocketCoreParams = RocketCoreParams(),
    icache: Option[ICacheParams] = Some(ICacheParams()),
    dcache: Option[DCacheParams] = Some(DCacheParams()),
    btb: Option[BTBParams] = Some(BTBParams()),
    dataScratchpadBytes: Int = 0,
    name: Option[String] = Some("tile"),
    hartId: Int = 0,
    beuAddr: Option[BigInt] = None,
    blockerCtrlAddr: Option[BigInt] = None,
    boundaryBuffers: Boolean = false // if synthesized with hierarchical PnR, cut feed-throughs?
    ) {

} 
