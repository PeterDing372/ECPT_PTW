// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package ECPT.PTW

import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import chisel3.withClock
import chisel3.internal.sourceinfo.SourceInfo
import chipsalliance.rocketchip.config._
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property

import scala.collection.mutable.ListBuffer
import freechips.rocketchip.rocket._



class debugPorts (implicit p : Parameters) extends MyCoreBundle()(p) {
  val debug_state = Output(UInt(3.W))
  val debug_counter = Output(UInt(log2Ceil(8).W))
  val debug_counter_trigger = Output(Bool())
  val req_addr = Output(UInt(vpnBits.W))
  
}


/** ECPT_PTW performs page table walk for high level TLB
  */
// class ECPT_PTW(n: Int)(implicit edge: TLEdgeOut, p: Parameters) extends CoreModule()(p) {
class ECPT_PTW(n: Int)(implicit p : Parameters) extends MyCoreModule()(p) {

  val io = IO(new Bundle {
    /* 1 TLB */
    val requestor = Flipped(new MyTLBPTWIO)
    /** to HellaCache */
    val mem = new MyHellaCacheIO
    /** to Core
      *
      * contains CSRs info and performance statistics
      */
    val dpath = new MyDatapathPTWIO
    val debug = new debugPorts
    val cache_valid = Input(Bool())
  })

  /* TODO: Fix this into parameters, temporary params */
  val init_pt_bits = 9
  val H1_poly : Long = 0x119
  val H2_poly : Long = 0x17d

  val s_ready :: s_hashing :: s_traverse1 :: s_traverse2 :: s_done :: Nil = Enum(5)
  val state_reg = RegInit(s_ready)
  val next_state = WireDefault(state_reg) // WireDefault makes sure the next state will remain current state
  state_reg := next_state // stage_reg assignment needs to be place above all other assignment
  
  /* Internal hardware modules */
  val counter = Module(new CounterWithTrigger(8))
  val H1_CRC = Module(new CRC_hash_FSM(init_pt_bits, H1_poly, 27)) // n: Int, g: Long, data_len: Int
  val H2_CRC = Module(new CRC_hash_FSM(init_pt_bits, H2_poly, 27))
  
  /* -------- START Variable declaration -------- */
  val tlb_req = Reg(new MyPTWReq)
  val vpn_h1 = Reg(UInt(init_pt_bits.W))
  val vpn_h2 = Reg(UInt(init_pt_bits.W))
  val both_hashing_done = WireDefault(false.B)
  val counter_trigger = WireDefault(false.B)
  
  /* -------- END Variable declaration -------- */

  /* Control Signals */
  H1_CRC.io.start := (state_reg === s_hashing)
  H2_CRC.io.start := (state_reg === s_hashing)
  both_hashing_done := (H1_CRC.io.done && H2_CRC.io.done)

  /* CRC module path statements */
  H1_CRC.io.data_in := Mux((state_reg === s_hashing), tlb_req.addr, 0.U)
  H2_CRC.io.data_in := Mux((state_reg === s_hashing), tlb_req.addr, 0.U)
  when(H1_CRC.io.done){
    vpn_h1 := H1_CRC.io.data_out
  }
  when(H2_CRC.io.done){
    vpn_h2 := H2_CRC.io.data_out
  } 
  // val cache_valid = true.B
  io.requestor.req.ready := (state_reg === s_ready)
  counter.io.trigger := counter_trigger
  counter_trigger := io.cache_valid && (state_reg === s_traverse1 || state_reg === s_traverse2)
  /* PTW FSM */
  switch (state_reg) {
    is (s_ready) {
      when(io.requestor.req.valid){
        tlb_req := io.requestor.req.bits.bits
      }
      next_state := Mux(io.requestor.req.valid, s_hashing, s_ready)
    }
    /* Parallel Computation of hash values */
    is(s_hashing){
      next_state := Mux(both_hashing_done, s_traverse1, s_hashing)
    }
    /* START: sequentially request the whole cacheline */
    is (s_traverse1) {
      next_state := Mux(counter.io.count === 7.U, s_traverse2, s_traverse1)
    }
    is (s_traverse2) {
      next_state := Mux(counter.io.count === 7.U, s_done, s_traverse2)
    }
    /* END: sequentially request the whole cacheline */
    is (s_done) {
      next_state := s_ready
    }
  }
  
  /* Debug signals */
  io.debug.debug_counter := counter.io.count
  io.debug.debug_state := state_reg
  io.debug.req_addr := tlb_req.addr
  io.debug.debug_counter_trigger := counter_trigger


  
  /* Note that the states corresponds to the rising clock edge, the resulting data will be updated */
  if(debug_flag) {
    printf("-------- DUT DEBUG INFO START --------\n")
    printf("req.valid: %d state_reg: %d next_state %d counter_trigger: %d\n", 
          io.requestor.req.valid, state_reg, next_state, counter_trigger)
    printf("tlb_req.addr: %d req.addr: %d\n", tlb_req.addr, io.requestor.req.bits.bits.addr)
    printf("vpn_h1: %d vpn_h2: %d H1_done: %d H2_done: %d\n", vpn_h1, vpn_h2, H1_CRC.io.done, H2_CRC.io.done)
    printf("-------- DUT DEBUG INFO END --------\n")
  }
  


  /* data path statements */


 

  /* two PTBR for 2-ary ECPT */
  // val ECPT_base1 = 0.U 
  // val ECPT_base2 = 0x1_0000.U // 4kB page size


  /* Setting the requst to cache */
  // mem request
  io.mem.req.valid := state_reg =/= s_ready || state_reg =/= s_done
  // io.mem.req.bits.addr := vaddr_hash1 : vaddr_hash2
  // io.mem.req.bits.idx.foreach(_ := vaddr_hash1) // vpn_h1

  

  /* ISA related singals, just follow original file assignment*/
  io.mem.req.bits.phys := true.B
  io.mem.req.bits.cmd  := M_XRD
  io.mem.req.bits.size := log2Ceil(xLen/8).U
  io.mem.req.bits.signed := false.B
  io.mem.req.bits.dv := false.B // do_both_stages && !stage2 // see note about mstatus in csr for details
  io.mem.req.bits.dprv := PRV.S.U   // PTW accesses are S-mode by definition

  // TODO: may change this when involve walk cache
  io.mem.s1_kill := false.B  // s1_kill: kill previous cycle's req
  io.mem.s2_kill := false.B
  
}

/** Mix-ins for constructing tiles that might have a PTW */
// trait CanHaveECPT_PTW extends HasTileParameters with HasHellaCache { this: BaseTile =>
//   val module: CanHaveECPT_PTWModule
//   var nPTWPorts = 1
//   nDCachePorts += usingPTW.toInt
// }

// trait CanHaveECPT_PTWModule extends HasHellaCacheModule {
//   val outer: CanHaveECPT_PTW
//   val ptwPorts = ListBuffer(outer.dcache.module.io.ptw)
//   val ptw = Module(new ECPT_PTW(outer.nPTWPorts)(outer.p))
//   ptw.io.mem <> DontCare
//   if (outer.usingPTW) {
//     dcachePorts += ptw.io.mem
//   }
// }