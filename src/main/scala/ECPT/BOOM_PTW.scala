// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package ECPT.PTW

import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import chisel3.withClock
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property
import freechips.rocketchip.rocket._
import ECPT.Debug._
import scala.collection.mutable.ListBuffer
import freechips.rocketchip.diplomacy.BufferParams


/** PTW contains L2TLB, and performs page table walk for high level TLB, and cache queries from L1 TLBs(I$, D$, RoCC)
  *
  * It performs hierarchy page table query to mem for the desired leaf PTE and cache them in l2tlb.
  * Besides leaf PTEs, it also caches non-leaf PTEs in pte_cache to accerlerate the process.
  *
  * ==Structure==
  *  - l2tlb : for leaf PTEs
  *   - set-associative (configurable with [[CoreParams.nL2TLBEntries]]and [[CoreParams.nL2TLBWays]]))
  *   - PLRU
  *  - pte_cache: for non-leaf PTEs
  *   - set-associative
  *   - LRU
  *  - s2_pte_cache: for non-leaf PTEs in 2-stage translation
  *   - set-associative
  *   - PLRU
  *
  * l2tlb Pipeline: 3 stage
  * {{{
  * stage 0 : read
  * stage 1 : decode
  * stage 2 : hit check
  * }}}
  * ==State Machine==
  * s_ready: ready to reveive request from TLB
  * s_req: request mem; pte_cache hit judge
  * s_wait1: deal with l2tlb error
  * s_wait2: final hit judge
  * s_wait3: receive mem response
  * s_fragment_superpage: for superpage PTE
  *
  * @note l2tlb hit happens in s_req or s_wait1
  * @see RV-priv spec 4.3-4.6 for Virtual-Memory System
  * @see RV-priv spec 8.5 for Two-Stage Address Translation
  * @todo details in two-stage translation
  */
class BOOM_PTW(n: Int)(implicit p: Parameters) extends CoreModule()(p) { 
  // implicit edge: TLEdgeOut,
  val io = IO(new Bundle {
    /** to n TLB */
    val requestor = Flipped(Vec(n, new TLBPTWIO))
    /** to HellaCache */
    val mem = new HellaCacheIO
    /** to Core
      *
      * contains CSRs info and performance statistics
      */
    val dpath = new DatapathPTWIO
    /** debug io ports
     * 
    */
    val debug = new PTW_DebugIO
  })

  val base_state_num = 8
  // val s_ready :: s_req :: s_wait1 :: s_dummy1 :: s_wait2 :: s_wait3 :: s_dummy2 :: s_fragment_superpage :: Nil 
  //     = Enum(base_state_num)
  /**
    * ==ECPT State Machine==
    * s_ready: ready to receive request from TLB
    * s_traverse{0-5}: traverse cacheline for 4KB, 2MB, 1GB pages
    * s_response: hit judgement, response PTE to tlb
    *             no hit will for a formatted PTE with 
    *             pf (page fault bit) set to response
    * s_done: transition back to s_ready, 
    *         this is when response to requstor is asserted 
    *         and response is sent back
  * */
  val s_ready :: s_hashing :: s_traverse0 :: s_traverse1 :: s_req :: s_wait1 :: s_done :: Nil 
      = Enum(7)
  val state = RegInit(s_ready)
  val l2_refill_wire = Wire(Bool())
  /** Arbiter to arbite request from n TLB */
  val arb = Module(new Arbiter(Valid(new PTWReq), n))
  // use TLB req as arbitor's input
  arb.io.in <> io.requestor.map(_.req)
  // receive req only when s_ready and not in refill
  
  /* Instantiate stage print */
  println(s"[Instantiation Start]")
  println(s"pgLevels: ${pgLevels} minPgLevels: ${minPgLevels} vpnBits: ${vpnBits}")
  println(s"hypervisorExtraAddrBits: ${hypervisorExtraAddrBits}")
  println(s"lgCacheBlockBytes: ${lgCacheBlockBytes} coreMaxAddrBits: ${coreMaxAddrBits}")
  // println(s"untagBits: ${untagBits}")
  println(s"[Instantiation End]")
  assert(!usingHypervisor && usingVM) // assert this is a single stage

  /* Temporary constant declaration*/
  // TODO: move into configs  
  val init_pt_bits = 9
  val H1_poly : Long = 0x119
  val H2_poly : Long = 0x17d

  // TODO: fix to compact regions, this is split into 4kb regions for readability
  val DRAM_BASE : Long = 0x8000_0000L
  val PT_BASE : Long = DRAM_BASE + 0x0200_0000L
  /* All pages are 4KB aligned */
  val base_4KB = VecInit((PT_BASE+0x0_0000L).U(coreMaxAddrBits.W), (PT_BASE+0x1_0000L).U(coreMaxAddrBits.W))
  // val base_4KB = VecInit(0x0000.U(coreMaxAddrBits.W), 0x1000.U(coreMaxAddrBits.W))
  // val base_2MB = VecInit(0x2000.U(coreMaxAddrBits.W), 0x3000.U(coreMaxAddrBits.W))
  // val base_1GB = VecInit(0x4000.U(coreMaxAddrBits.W), 0x5000.U(coreMaxAddrBits.W))
  

  val resp_valid = RegNext(VecInit(Seq.fill(io.requestor.size)(false.B)))

  val clock_en = state =/= s_ready || l2_refill_wire || arb.io.out.valid || 
                  io.dpath.sfence.valid || io.dpath.customCSRs.disableDCacheClockGate
  io.dpath.clock_enabled := usingVM.B && clock_en
  /* Operation stage print */
  arb.io.out.ready := (state === s_ready) && !l2_refill_wire
  val debug_flag = false
  if (debug_flag) {
    printf("[BOOM PTW, clock_en %d]: input valid: %d output valid: %d\n", 
          clock_en,
          io.requestor(0).req.valid,  arb.io.out.valid)
    printf("[BOOM PTW]: output bits valid: %d output fire: %d\n", 
            arb.io.out.bits.valid,  arb.io.out.fire())
  }
  
  val gated_clock =
    if (!usingVM || !tileParams.dcache.get.clockGate) clock
    else ClockGate(clock, clock_en, "ptw_clock_gate")
  withClock (gated_clock) { 
  /* -------- entering gated-clock domain --------- */
  val hashed_vpns = Reg(Vec(2, UInt(init_pt_bits.W)))
  // val hashed_vpns(0) = Reg(UInt(init_pt_bits.W))
  // val hashed_vpns(1) = Reg(UInt(init_pt_bits.W))
  val cached_PTE_lines = Reg(Vec(2, new EC_PTE_CacheLine))
  // val cached_line_T1 = Reg(new EC_PTE_CacheLine)
  // val cached_line_T2 = Reg(new EC_PTE_CacheLine)
  val traverse_count = Reg(UInt(lgCacheBlockBytes.W)) 
  // NOTE: this is reg because original mem_resp and data are reg
  val both_hashing_done = WireDefault(false.B)
  // TODO: check if original traverse logic can be removed
  val line_addr = WireInit(0.U(coreMaxAddrBits.W))
  val line_offset = WireInit(0.U(coreMaxAddrBits.W))

  val invalidated = Reg(Bool())
  /** current PTE level
    * {{{
    * 0 <= count <= pgLevel-1
    * count = pgLevel - 1 : leaf PTE
    * count < pgLevel - 1 : non-leaf PTE
    * }}}
    */
  val count = Reg(UInt(log2Ceil(pgLevels).W))
  val resp_ae_ptw = Reg(Bool())
  val resp_ae_final = Reg(Bool())
  val resp_pf = Reg(Bool())
  val resp_gf = Reg(Bool())
  val resp_hr = Reg(Bool())
  val resp_hw = Reg(Bool())
  val resp_hx = Reg(Bool())
  val resp_fragmented_superpage = Reg(Bool())

  /** tlb request */
  val r_req = Reg(new PTWReq)
  /** current selected way in arbitor */
  val r_req_dest = Reg(Bits())
  // to respond to L1TLB : l2_hit
  // to construct mem.req.addr
  val r_pte = Reg(new PTE)
  val r_hgatp = Reg(new PTBR)
  // 2-stage pageLevel
  val aux_count = Reg(UInt(log2Ceil(pgLevels).W))
  /** pte for 2-stage translation */
  val aux_pte = Reg(new PTE)
  val aux_ppn_hi = (pgLevels > 4 && r_req.addr.getWidth > aux_pte.ppn.getWidth).option(Reg(UInt((r_req.addr.getWidth - aux_pte.ppn.getWidth).W)))
  val gpa_pgoff = Reg(UInt(pgIdxBits.W)) // only valid in resp_gf case
  val stage2 = Reg(Bool())
  val stage2_final = Reg(Bool())

  val satp = Mux(arb.io.out.bits.bits.vstage1, io.dpath.vsatp, io.dpath.ptbr)
  val r_hgatp_initial_count = pgLevels.U - minPgLevels.U - r_hgatp.additionalPgLevels
  /** 2-stage translation both enable */
  val do_both_stages = r_req.vstage1 && r_req.stage2
  val max_count = count max aux_count
  val vpn = Mux(r_req.vstage1 && stage2, aux_pte.ppn, r_req.addr)

  val mem_resp_valid = RegNext(io.mem.resp.valid)
  // why are these reg? to reduce critical path!
  val mem_resp_data = RegNext(io.mem.resp.bits.data)
  io.mem.uncached_resp.map { resp =>
    assert(!(resp.valid && io.mem.resp.valid)) // at most one can be valid simultaneously 
    resp.ready := true.B
    when (resp.valid) {
      mem_resp_valid := true.B
      mem_resp_data := resp.bits.data
    }
  }

  // construct pte from mem.resp
  val (pte, invalid_paddr) = { 
    val tmp = mem_resp_data.asTypeOf(new PTE())
    val res = WireDefault(tmp)
    res.ppn := Mux(do_both_stages && !stage2, tmp.ppn(vpnBits.min(tmp.ppn.getWidth)-1, 0), tmp.ppn(ppnBits-1, 0))
    // printf(s"[BOOM_PTW] reserved for future bits %d\n", tmp.reserved_for_future)
    // ppnBits: 32 - 12 = 20
    // TODO: this is commented as ECPT has no mid-level PTE
    // when (tmp.r || tmp.w || tmp.x) { // when it is NOT leaf PTE
    //   // for superpage mappings, make sure PPN LSBs are zero
    //   for (i <- 0 until pgLevels-1)
    //     when (count <= i.U && tmp.ppn((pgLevels-1-i)*pgLevelBits-1, (pgLevels-2-i)*pgLevelBits) =/= 0.U) { res.v := false.B }
    // }
    (res, Mux(do_both_stages && !stage2, (tmp.ppn >> vpnBits) =/= 0.U, (tmp.ppn >> ppnBits) =/= 0.U))
    // invalid_paddr:  non-zero MSB bits larger than addrBits range is invalid 
  }

   /* Internal ECPT hardware modules */
  val counter = Module(new CounterWithTrigger(8))
  val H1_CRC = Module(new CRC_hash_FSM(init_pt_bits, H1_poly, vpnBits)) // n: Int, g: Long, data_len: Int
  val H2_CRC = Module(new CRC_hash_FSM(init_pt_bits, H2_poly, vpnBits))

  when (H1_CRC.io.done) {
    hashed_vpns(0) := H1_CRC.io.data_out
  }
  when (H2_CRC.io.done) {
    hashed_vpns(1) := H2_CRC.io.data_out
  } 
  
  counter.io.trigger := io.mem.resp.valid && (state === s_traverse0 || state === s_traverse1)
  traverse_count := counter.io.count

  /* CRC Hash Control Signals */
  H1_CRC.io.start := (state === s_hashing)
  H2_CRC.io.start := (state === s_hashing)
  both_hashing_done := (H1_CRC.io.done && H2_CRC.io.done)

  H1_CRC.io.data_in := Mux((state === s_hashing), r_req.addr, 0.U)
  H2_CRC.io.data_in := Mux((state === s_hashing), r_req.addr, 0.U)


  /* load pte to in-module register */
  when (mem_resp_valid) {
    when (state === s_traverse0) {
      // cached_line_T1.ptes(traverse_count) := pte
      cached_PTE_lines(0).ptes(traverse_count) := pte
    } .elsewhen (state === s_traverse1) {
      // cached_line_T2.ptes(traverse_count) := pte
      cached_PTE_lines(1).ptes(traverse_count) := pte

    }
    // assert(state === s_traverse0 || state === s_traverse1) 
    // TODO: uncomment this when PTW connect to actual cache
  }


  // tag hit judgement
  val ECPT_tag_hit = VecInit(Seq.fill(2)(false.B))
  val match_tag = WireDefault(0.U(27.W)) // TODO: replace this to a vector?
  val ECPT_hit_way = OHToUInt(ECPT_tag_hit)
  val ptw_has_hit = WireDefault(false.B)
  val hit_pte = Wire(new PTE)
  for (way <- 0 until 2) { // TODO: replace the number 2 to a parameter in config/params
    ECPT_tag_hit(way) := (cached_PTE_lines(way).fetchTag4KB === match_tag)
    // TODO: try to assert one hot here? how to verify this
  }
  match_tag := r_req.addr(26, 3) // TODO: is this affected for larger pages? no just pad with zeros
  val ECPT_tag_hit_AsInt = Cat(ECPT_tag_hit.reverse)
  // TODO: add & condition for now can remove later
  ptw_has_hit := ECPT_tag_hit_AsInt =/= 0.U && state === s_done 
  assert(ECPT_hit_way < 2.U) // make sure hit way does not exceed limit
  val blockOffsetMask = 0x38.U // which 8 byte block
  /* hashed_vpns: in line offset is the lower 3 bits */
  val pteInlineAddr = (hashed_vpns(ECPT_hit_way) & 0x7.U)
  hit_pte := cached_PTE_lines(ECPT_hit_way).ptes(pteInlineAddr)
  // ptw_has_hit, hit_pte, 
  

  /** stage2_pte_cache input addr */
  // TODO: remove all related logic to this

  def makeFragmentedSuperpagePPN(ppn: UInt): Seq[UInt] = {
    (pgLevels-1 until 0 by -1).map(i => Cat(ppn >> (pgLevelBits*i), r_req.addr(((pgLevelBits*i) min vpnBits)-1, 0).padTo(pgLevelBits*i)))
  }
 
  
  // pte_cache hit or 2-stage pte_cache hit
  val pte_hit = RegNext(false.B)
  io.dpath.perf.pte_miss := false.B
  io.dpath.perf.pte_hit := pte_hit && (state === s_req) && !io.dpath.perf.l2hit
  assert(!(io.dpath.perf.l2hit && (io.dpath.perf.pte_miss || io.dpath.perf.pte_hit)),
    "PTE Cache Hit/Miss Performance Monitor Events are lower priority than L2TLB Hit event")
  // l2_refill happens when find the leaf pte
  val l2_refill = RegNext(false.B)
  l2_refill_wire := l2_refill
  io.dpath.perf.l2miss := false.B
  io.dpath.perf.l2hit := false.B
  /* --------------- l2tlb START --------------------- */
  val (l2_hit, l2_error, l2_pte, l2_tlb_ram) = if (coreParams.nL2TLBEntries == 0) 
    (false.B, false.B, WireDefault(0.U.asTypeOf(new PTE)), None) 
    else {
    val code = new ParityCode
    require(isPow2(coreParams.nL2TLBEntries))
    require(isPow2(coreParams.nL2TLBWays))
    require(coreParams.nL2TLBEntries >= coreParams.nL2TLBWays)
    val nL2TLBSets = coreParams.nL2TLBEntries / coreParams.nL2TLBWays
    require(isPow2(nL2TLBSets))
    val idxBits = log2Ceil(nL2TLBSets)

    val l2_plru = new SetAssocLRU(nL2TLBSets, coreParams.nL2TLBWays, "plru")

    val ram =  DescribedSRAM(
      name = "l2_tlb_ram",
      desc = "L2 TLB",
      size = nL2TLBSets,
      data = Vec(coreParams.nL2TLBWays, UInt(code.width(new L2TLBEntry(nL2TLBSets).getWidth).W))
      /**
       * The data is: code.width adds one bit to the original width
       * And the +1 bit is added to the base L2TLBEntry bit size
      
       */
    )

    val g = Reg(Vec(coreParams.nL2TLBWays, UInt(nL2TLBSets.W)))
    val valid = RegInit(VecInit(Seq.fill(coreParams.nL2TLBWays)(0.U(nL2TLBSets.W))))
    // use r_req to construct tag
    val (r_tag, r_idx) = Split(Cat(r_req.vstage1, r_req.addr(maxSVAddrBits-pgIdxBits-1, 0)), idxBits)
    /** the valid vec for the selected set(including n ways) */
    val r_valid_vec = valid.map(_(r_idx)).asUInt
    val r_valid_vec_q = Reg(UInt(coreParams.nL2TLBWays.W))
    val r_l2_plru_way = Reg(UInt(log2Ceil(coreParams.nL2TLBWays max 1).W))
    r_valid_vec_q := r_valid_vec
    // replacement way
    r_l2_plru_way := (if (coreParams.nL2TLBWays > 1) l2_plru.way(r_idx) else 0.U)
    // refill with r_pte(leaf pte)
    when (l2_refill && !invalidated) {
      val entry = Wire(new L2TLBEntry(nL2TLBSets))
      entry.ppn := r_pte.ppn
      entry.d := r_pte.d
      entry.a := r_pte.a
      entry.u := r_pte.u
      entry.x := r_pte.x
      entry.w := r_pte.w
      entry.r := r_pte.r
      entry.tag := r_tag
      // if all the way are valid, use plru to select one way to be replaced,
      // otherwise use PriorityEncoderOH to select one
      val wmask = if (coreParams.nL2TLBWays > 1) Mux(r_valid_vec_q.andR, UIntToOH(r_l2_plru_way, coreParams.nL2TLBWays), PriorityEncoderOH(~r_valid_vec_q)) else 1.U(1.W)
      ram.write(r_idx, VecInit(Seq.fill(coreParams.nL2TLBWays)(code.encode(entry.asUInt))), wmask.asBools)

      val mask = UIntToOH(r_idx)
      for (way <- 0 until coreParams.nL2TLBWays) {
        when (wmask(way)) {
          valid(way) := valid(way) | mask
          g(way) := Mux(r_pte.g, g(way) | mask, g(way) & ~mask)
        }
      }
    }
    // sfence happens
    when (io.dpath.sfence.valid) {
      val hg = usingHypervisor.B && io.dpath.sfence.bits.hg
      for (way <- 0 until coreParams.nL2TLBWays) {
        valid(way) :=
          Mux(!hg && io.dpath.sfence.bits.rs1, valid(way) & ~UIntToOH(io.dpath.sfence.bits.addr(idxBits+pgIdxBits-1, pgIdxBits)),
          Mux(!hg && io.dpath.sfence.bits.rs2, valid(way) & g(way),
          0.U))
      }
    }

    val s0_valid = !l2_refill && arb.io.out.fire()
    val s0_suitable = arb.io.out.bits.bits.vstage1 === arb.io.out.bits.bits.stage2 && !arb.io.out.bits.bits.need_gpa
    val s1_valid = RegNext(s0_valid && s0_suitable && arb.io.out.bits.valid)
    val s2_valid = RegNext(s1_valid)
    // read from tlb idx
    val s1_rdata = ram.read(arb.io.out.bits.bits.addr(idxBits-1, 0), s0_valid)
    val s2_rdata = s1_rdata.map(s1_rdway => code.decode(RegEnable(s1_rdway, s1_valid)))
    val s2_valid_vec = RegEnable(r_valid_vec, s1_valid)
    val s2_g_vec = RegEnable(VecInit(g.map(_(r_idx))), s1_valid)
    val s2_error = (0 until coreParams.nL2TLBWays).map(way => s2_valid_vec(way) && s2_rdata(way).error).orR
    when (s2_valid && s2_error) { valid.foreach { _ := 0.U }}
    // decode
    val s2_entry_vec = s2_rdata.map(_.uncorrected.asTypeOf(new L2TLBEntry(nL2TLBSets)))
    val s2_hit_vec = (0 until coreParams.nL2TLBWays).map(way => s2_valid_vec(way) && (r_tag === s2_entry_vec(way).tag))
    val s2_hit = s2_valid && s2_hit_vec.orR
    io.dpath.perf.l2miss := s2_valid && !(s2_hit_vec.orR)
    io.dpath.perf.l2hit := s2_hit
    when (s2_hit) {
      l2_plru.access(r_idx, OHToUInt(s2_hit_vec))
      assert((PopCount(s2_hit_vec) === 1.U) || s2_error, "L2 TLB multi-hit")
    }

    val s2_pte = Wire(new PTE)
    val s2_hit_entry = Mux1H(s2_hit_vec, s2_entry_vec)
    s2_pte.ppn := s2_hit_entry.ppn
    s2_pte.d := s2_hit_entry.d
    s2_pte.a := s2_hit_entry.a
    s2_pte.g := Mux1H(s2_hit_vec, s2_g_vec)
    s2_pte.u := s2_hit_entry.u
    s2_pte.x := s2_hit_entry.x
    s2_pte.w := s2_hit_entry.w
    s2_pte.r := s2_hit_entry.r
    s2_pte.v := true.B
    s2_pte.reserved_for_future := 0.U // TODO: change this to ECPT tag
    s2_pte.reserved_for_software := 0.U

    for (way <- 0 until coreParams.nL2TLBWays) {
      ccover(s2_hit && s2_hit_vec(way), s"L2_TLB_HIT_WAY$way", s"L2 TLB hit way$way")
    }

    (s2_hit, s2_error, s2_pte, Some(ram))
  }
  /* --------------- l2tlb END --------------------- */

  // if SFENCE occurs during walk, don't refill PTE cache or L2 TLB until next walk
  invalidated := io.dpath.sfence.valid || (invalidated && state =/= s_ready)
  // mem request
  // io.mem.req.valid := state === s_req 
  io.mem.req.valid := state === s_traverse0 || state === s_traverse1
  io.mem.req.bits.phys := true.B
  io.mem.req.bits.cmd  := M_XRD // read operation
  io.mem.req.bits.size := log2Ceil(xLen/8).U
  io.mem.req.bits.signed := false.B
  // io.mem.req.bits.addr := pte_addr
  io.mem.req.bits.addr := (line_addr | line_offset) 
  // io.mem.req.bits.idx.foreach(_ := pte_addr) 
  io.mem.req.bits.idx.foreach(_ := line_addr) 
  // this is used if single cache bank is larger than page size
  io.mem.req.bits.dprv := PRV.S.U   // PTW accesses are S-mode by definition
  io.mem.req.bits.dv := do_both_stages && !stage2
  io.mem.s1_kill := l2_hit || state =/= s_wait1 // TODO: replace with with condition of hit in PWC
  io.mem.s2_kill := false.B

  val pageGranularityPMPs = pmpGranularity >= (1 << pgIdxBits) 
  // checks that the pmpGranularity is larger than 4KB
  require(!usingHypervisor || pageGranularityPMPs, s"hypervisor requires pmpGranularity >= ${1<<pgIdxBits}")
  // TODO: how to make PMP PMA work is this static?
  // val pmaPgLevelHomogeneous = (0 until pgLevels) map { i =>
  //   val pgSize = BigInt(1) << (pgIdxBits + ((pgLevels - 1 - i) * pgLevelBits))
  //   if (pageGranularityPMPs && i == pgLevels - 1) {
  //     require(TLBPageLookup.homogeneous(edge.manager.managers, pgSize), s"All memory regions must be $pgSize-byte aligned")
  //     true.B
  //   } else {
  //     TLBPageLookup(edge.manager.managers, xLen, p(CacheBlockBytes), pgSize)(r_pte.ppn << pgIdxBits).homogeneous
  //   }
  // }
  // val pmaHomogeneous = pmaPgLevelHomogeneous(count)
  // val pmpHomogeneous = new PMPHomogeneityChecker(io.dpath.pmp).apply(r_pte.ppn << pgIdxBits, count)
  // val homogeneous = pmaHomogeneous && pmpHomogeneous
  val homogeneous = true.B // indicates if memory region share the same attribute
  // response to tlb
  for (i <- 0 until io.requestor.size) {
    io.requestor(i).resp.valid := resp_valid(i)
    io.requestor(i).resp.bits.ae_ptw := resp_ae_ptw
    io.requestor(i).resp.bits.ae_final := resp_ae_final
    io.requestor(i).resp.bits.pf := resp_pf
    io.requestor(i).resp.bits.gf := resp_gf
    io.requestor(i).resp.bits.hr := resp_hr
    io.requestor(i).resp.bits.hw := resp_hw
    io.requestor(i).resp.bits.hx := resp_hx
    io.requestor(i).resp.bits.pte := r_pte
    io.requestor(i).resp.bits.level := max_count
    io.requestor(i).resp.bits.homogeneous := homogeneous || pageGranularityPMPs.B
    io.requestor(i).resp.bits.fragmented_superpage := resp_fragmented_superpage && pageGranularityPMPs.B
    io.requestor(i).resp.bits.gpa.valid := r_req.need_gpa
    io.requestor(i).resp.bits.gpa.bits :=
      Cat(Mux(!stage2_final || !r_req.vstage1 || aux_count === (pgLevels - 1).U, aux_pte.ppn, makeFragmentedSuperpagePPN(aux_pte.ppn)(aux_count)), gpa_pgoff)
    io.requestor(i).resp.bits.gpa_is_pte := !stage2_final
    io.requestor(i).ptbr := io.dpath.ptbr
    io.requestor(i).hgatp := io.dpath.hgatp
    io.requestor(i).vsatp := io.dpath.vsatp
    io.requestor(i).customCSRs := io.dpath.customCSRs
    io.requestor(i).status := io.dpath.status
    io.requestor(i).hstatus := io.dpath.hstatus
    io.requestor(i).gstatus := io.dpath.gstatus
    io.requestor(i).pmp := io.dpath.pmp
  }

  // control state machine
  val next_state = WireDefault(state)
  state := OptimizationBarrier(next_state)
  val do_switch = WireDefault(false.B)

  switch (state) {
    is (s_ready) {
      when (arb.io.out.fire()) {
        val satp_initial_count = pgLevels.U - minPgLevels.U - satp.additionalPgLevels
        // initial count to start with: 3 - 3 - 0
        val vsatp_initial_count = pgLevels.U - minPgLevels.U - io.dpath.vsatp.additionalPgLevels
        val hgatp_initial_count = pgLevels.U - minPgLevels.U - io.dpath.hgatp.additionalPgLevels
        val aux_ppn             = Mux(arb.io.out.bits.bits.vstage1, io.dpath.vsatp.ppn, arb.io.out.bits.bits.addr)

        r_req := arb.io.out.bits.bits
        r_req_dest := arb.io.chosen
        // next_state := Mux(arb.io.out.bits.valid, s_req, s_ready)
        next_state := Mux(arb.io.out.bits.valid, s_hashing, s_ready)
        stage2       := arb.io.out.bits.bits.stage2
        stage2_final := arb.io.out.bits.bits.stage2 && !arb.io.out.bits.bits.vstage1
        count       := Mux(arb.io.out.bits.bits.stage2, hgatp_initial_count, satp_initial_count)
        aux_count   := Mux(arb.io.out.bits.bits.vstage1, vsatp_initial_count, 0.U)
        aux_pte.ppn := aux_ppn
        aux_ppn_hi.foreach { _ := aux_ppn >> aux_pte.ppn.getWidth }
        aux_pte.reserved_for_future := 0.U // TODO: what to do with this?
        resp_ae_ptw := false.B
        resp_ae_final := false.B
        resp_pf := false.B
        resp_gf := false.B
        resp_hr := true.B
        resp_hw := true.B
        resp_hx := true.B
        resp_fragmented_superpage := false.B
        r_hgatp := io.dpath.hgatp

        assert(!arb.io.out.bits.bits.need_gpa || arb.io.out.bits.bits.stage2)
      }
    }
    is (s_hashing) {
      next_state := Mux(both_hashing_done, s_traverse0, s_hashing)
    }
    is (s_traverse0) {
      // printf("[BOOM_PTW] reached s_traverse0\n")
      next_state := Mux(traverse_count === 7.U, s_traverse1, s_traverse0)
      line_addr := (hashed_vpns(0) & "hFFFFFFF8".U) | base_4KB(0)
      // check line_addr 64 byte alignment
      line_offset := (1.U << 3) * (traverse_count)
      assert((line_addr & 0x3.U) === 0.U, "[BOOM_PTW] line_addr not 64 byte aligned")
      // TODO: this can be replace with property?
      // returns immediately when there is read access exception
      when (io.mem.s2_xcpt.ae.ld) { // potentially alignment exception
        resp_ae_ptw := true.B
        next_state := s_ready
        resp_valid(r_req_dest) := true.B
      }
    }
    is (s_traverse1) {
      next_state := Mux(traverse_count === 7.U, s_done, s_traverse1)
      // remove lower 3 bits for 64 byte alignment
      line_addr := (hashed_vpns(0) & "hFFFFFFF8".U) | base_4KB(1) 
      // check line_addr 64 byte alignment
      line_offset := (1.U << 3) * (traverse_count)
      assert((line_addr & 0x3.U) === 0.U, "[BOOM_PTW] line_addr not 64 byte aligned")
      // returns immediately when there is read access exception
      when (io.mem.s2_xcpt.ae.ld) {
        resp_ae_ptw := true.B
        next_state := s_ready
        resp_valid(r_req_dest) := true.B
      }
    }
    is (s_done) { // consider change this to a success condition state
      // TODO 
      next_state := s_ready
    }
    is (s_req) {
      // when(stage2 && count === r_hgatp_initial_count) {
      //   gpa_pgoff := Mux(aux_count === (pgLevels-1).U, r_req.addr << (xLen/8).log2, stage2_pte_cache_addr)
      // }
      // pte_cache hit
      next_state := Mux(io.mem.req.ready, s_wait1, s_req)
      // requires receiver to be ready then goes to s_wait1 stage
      printf("[BOOM_PTW]: s_req execution directly falls in last case, going to next state\n")
      // when (stage2_pte_cache_hit) {
      //   aux_count := aux_count + 1.U
      //   aux_pte.ppn := stage2_pte_cache_data
      //   aux_ppn_hi.foreach { _ := 0.U }
      //   aux_pte.reserved_for_future := 0.U
      //   pte_hit := true.B
      // }.elsewhen (pte_cache_hit) {
      //   count := count + 1.U
      //   pte_hit := true.B
      // }.otherwise {
      //   next_state := Mux(io.mem.req.ready, s_wait1, s_req)
      //   // requires receiver to be ready then goes to s_wait1 stage
      //   printf("[BOOM_PTW]: s_req execution directly falls in last case, going to next state\n")
      // }

    }
    // TODO: add additional states here
    // is (s_wait1) {
    //   // This Mux is for the l2_error case; the l2_hit && !l2_error case is overriden below
    //   next_state := Mux(l2_hit, s_req, s_wait2)
    // }
    // is (s_wait2) {
    //   next_state := s_wait3
    //   io.dpath.perf.pte_miss := count < (pgLevels-1).U
    //   when (io.mem.s2_xcpt.ae.ld) {
    //     resp_ae_ptw := true.B
    //     next_state := s_ready
    //     resp_valid(r_req_dest) := true.B
    //   }
    // }
    // is (s_fragment_superpage) {
    //   next_state := s_ready
    //   resp_valid(r_req_dest) := true.B
    //   when (!homogeneous) {
    //     count := (pgLevels-1).U
    //     resp_fragmented_superpage := true.B 
    // TODO: add resp_fragmented_superpage logic in hit judgement
    //   }
    //   when (do_both_stages) {
    //     resp_fragmented_superpage := true.B
    //   }
    // }
  }

  val merged_pte = { // TODO: this is something that merge hgatp and vsatp?
    val superpage_masks = (0 until pgLevels).map(i => 
                          ((BigInt(1) << pte.ppn.getWidth) - (BigInt(1) << (pgLevels-1-i)*pgLevelBits)).U)
                          // (1 << 44) - (1 << ((2 - i) * 9))
    val superpage_mask = superpage_masks(Mux(stage2_final, max_count, (pgLevels-1).U))
    /**
      * get current level mask, stage_final == "only a stage 2 request", 
      * max_count: aux_count max count
      * pgLevels-1: 2
      **/
    val stage1_ppns = (0 until pgLevels-1).map(i => 
                      Cat(pte.ppn(pte.ppn.getWidth-1, (pgLevels-i-1)*pgLevelBits), 
                      aux_pte.ppn((pgLevels-i-1)*pgLevelBits-1,0))) :+ pte.ppn
    val stage1_ppn = stage1_ppns(count)
    makePTE(stage1_ppn & superpage_mask, aux_pte)
  }

  // r_pte := OptimizationBarrier( // result pte, TODO: replace condition for final hit to ECPT
  //   // l2tlb hit->find a leaf PTE(l2_pte), respond to L1TLB
  //   Mux(l2_hit && !l2_error, l2_pte,
  //   // pte cache hit->find a non-leaf PTE(pte_cache),continue to request mem
  //   Mux(state === s_req && !stage2_pte_cache_hit && pte_cache_hit, makePTE(pte_cache_data, l2_pte),
  //   // 2-stage translation
  //   Mux(do_switch, makeHypervisorRootPTE(r_hgatp, pte.ppn, r_pte),
  //   // when mem respond, store mem.resp.pte
  //   Mux(mem_resp_valid, Mux(!traverse && r_req.vstage1 && stage2, merged_pte, pte),
  //   // fragment_superpage
  //   Mux(state === s_fragment_superpage && !homogeneous, makePTE(makeFragmentedSuperpagePPN(r_pte.ppn)(count), r_pte),
  //   // when tlb request come->request mem, use root address in satp(or vsatp,hgatp)
  //   Mux(arb.io.out.fire(), Mux(arb.io.out.bits.bits.stage2, makeHypervisorRootPTE(io.dpath.hgatp, io.dpath.vsatp.ppn, r_pte), makePTE(satp.ppn, r_pte)),
  //   r_pte)))))))
  // result pte is originally placed here for L2TLB judgement
  r_pte := OptimizationBarrier( // TODO: replace condition for final hit to ECPT, 
    // l2tlb hit->find a leaf PTE(l2_pte), respond to L1TLB
    Mux(l2_hit && !l2_error, l2_pte,
    // when parallel traverse hit, store mem.resp.pte to L2 and select
    Mux(ptw_has_hit, hit_pte, 
    // fragment_superpage
    Mux(false.B && !homogeneous, makePTE(makeFragmentedSuperpagePPN(r_pte.ppn)(count), r_pte), 
    // TODO: assumed homogeneous
    // when tlb request come->request mem, use root address in satp(or vsatp,hgatp) 
    // this is first stage of traverse
    Mux(arb.io.out.fire(), Mux(arb.io.out.bits.bits.stage2, makeHypervisorRootPTE(io.dpath.hgatp, io.dpath.vsatp.ppn, r_pte), makePTE(satp.ppn, r_pte)),
    r_pte)))))


  when (l2_hit && !l2_error) {
    assert(state === s_req || state === s_wait1)
    next_state := s_ready
    resp_valid(r_req_dest) := true.B
    count := (pgLevels-1).U
  } // TODO: check what to do with l2_hit

  when (ptw_has_hit) {
    resp_valid(r_req_dest) := true.B
  }

  when (mem_resp_valid) {
    // assert(state === s_wait3) // TODO: find something to replace this assert
    // next_state := s_req // default to s_req, will be overrided
    // when (traverse) { // TODO: Replace with ECPT, add radix traverse
    //   when (do_both_stages && !stage2) { do_switch := true.B }
    //   count := count + 1.U
    // }.otherwise {
      // guest page fault
      val gf = stage2 && !stage2_final && !pte.ur()
      // access exception
      val ae = pte.v && invalid_paddr
      // page fault
      val pf = pte.v && pte.reserved_for_future(9,3) =/= 0.U
      val success = pte.v && !ae && !pf && !gf 
      // definition of success: 

      when (do_both_stages && !stage2_final && success) { 
        // TODO: check this logic flow, currently !usingHypervisor so doesn't care
        when (stage2) {
          stage2 := false.B
          count := aux_count
        }.otherwise {
          stage2_final := true.B
          do_switch := true.B
        }
      }.otherwise { // find a leaf pte, start l2 refill
        
        // TODO: refill logic is also different
        // l2_refill := success && count === (pgLevels-1).U && !r_req.need_gpa &&
        //   (!r_req.vstage1 && !r_req.stage2 ||
        //    do_both_stages && aux_count === (pgLevels-1).U && pte.isFullPerm())
        count := max_count

        // TODO: check what theses need to change
        // when (pageGranularityPMPs.B && !(count === (pgLevels-1).U && (!do_both_stages || aux_count === (pgLevels-1).U))) {
        //   next_state := s_fragment_superpage
        // }.otherwise {
          // next_state := s_ready
          // resp_valid(r_req_dest) := true.B
        // }
        

        resp_ae_final := ae
        resp_pf := pf && !stage2
        resp_gf := gf || (pf && stage2)
        resp_hr := !stage2 || (!pf && !gf && pte.ur())
        resp_hw := !stage2 || (!pf && !gf && pte.uw())
        resp_hx := !stage2 || (!pf && !gf && pte.ux())
      }
    // }
  }
  when (io.mem.s2_nack) {
    // assert(state === s_wait2) // TODO: find something to replace this assert 
    next_state := s_req
  }

  // when (do_switch) { // do_switch: switching from supervisor to hypervisor, default to false for no Virtual Machine
  //   aux_count := Mux(traverse, count + 1.U, count)
  //   count := r_hgatp_initial_count
  //   aux_pte := Mux(traverse, pte, {
  //     val s1_ppns = (0 until pgLevels-1).map(i => Cat(pte.ppn(pte.ppn.getWidth-1, (pgLevels-i-1)*pgLevelBits), r_req.addr(((pgLevels-i-1)*pgLevelBits min vpnBits)-1,0).padTo((pgLevels-i-1)*pgLevelBits))) :+ pte.ppn
  //     makePTE(s1_ppns(count), pte)
  //   })
  //   aux_ppn_hi.foreach { _ := 0.U }
  //   stage2 := true.B
  // }

  for (i <- 0 until pgLevels) {
    // val leaf = mem_resp_valid && !traverse && count === i.U 
    val leaf = true.B
    // this leaf signal controls condition for verification
    // TODO: check if any of these statement can be resused
    // currently hard-wire leaf to true
    ccover(leaf && pte.v && !invalid_paddr && pte.reserved_for_future(9,3) === 0.U, s"L$i", s"successful page-table access, level $i")
    ccover(leaf && pte.v && invalid_paddr, s"L${i}_BAD_PPN_MSB", s"PPN too large, level $i")
    ccover(leaf && pte.v && pte.reserved_for_future(9,3) =/= 0.U, s"L${i}_BAD_RSV_MSB", s"reserved MSBs set, level $i")
    ccover(leaf && !mem_resp_data(0), s"L${i}_INVALID_PTE", s"page not present, level $i")
    if (i != pgLevels-1)
      ccover(leaf && !pte.v && mem_resp_data(0), s"L${i}_BAD_PPN_LSB", s"PPN LSBs not zero, level $i")
  }
  ccover(mem_resp_valid && count === (pgLevels-1).U && pte.table(), s"TOO_DEEP", s"page table too deep")
  ccover(io.mem.s2_nack, "NACK", "D$ nacked page-table access")
  // ccover(state === s_wait2 && io.mem.s2_xcpt.ae.ld, "AE", "access exception while walking page table") // TODO: check this

  /* -------- [DEBUG] Connection for all debug io -------- */
  io.debug.r_req_input := io.requestor(0).req.bits.bits
  io.debug.r_req_arb := r_req
  io.debug.ptwState := state
  io.debug.other_logic.vpn := vpn
  io.debug.other_logic.do_both_stages := do_both_stages
  io.debug.other_logic.line_addr := line_addr
  io.debug.other_logic.arbOutValid := arb.io.out.valid
  io.debug.cached_line_T1 := cached_PTE_lines(0)// cached_line_T1
  io.debug.cached_line_T2 := cached_PTE_lines(1)// cached_line_T2
  io.debug.tagT0 := cached_PTE_lines(0).fetchTag4KB
  io.debug.tagT1 := cached_PTE_lines(1).fetchTag4KB
  io.debug.ECPT_tag_hit := ECPT_tag_hit
  io.debug.ECPT_hit_way := ECPT_hit_way
  io.debug.pteInlineAddr := pteInlineAddr

  
  

  } 
  /* ------------ leaving gated-clock domain ------------*/ 

  private def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
    if (usingVM) property.cover(cond, s"PTW_$label", "MemorySystem;;" + desc)

  /** Relace PTE.ppn with ppn */
  private def makePTE(ppn: UInt, default: PTE) = {
    val pte = WireDefault(default)
    pte.ppn := ppn
    pte
  }
  /** use hgatp and vpn to construct a new ppn */
  private def makeHypervisorRootPTE(hgatp: PTBR, vpn: UInt, default: PTE) = {
    val count = pgLevels.U - minPgLevels.U - hgatp.additionalPgLevels
    val idxs = (0 to pgLevels-minPgLevels).map(i => (vpn >> (pgLevels-i)*pgLevelBits))
    val lsbs = WireDefault(t = UInt(maxHypervisorExtraAddrBits.W), init = idxs(count))
    val pte = WireDefault(default)
    pte.ppn := Cat(hgatp.ppn >> maxHypervisorExtraAddrBits, lsbs)
    pte
  }

  

}
