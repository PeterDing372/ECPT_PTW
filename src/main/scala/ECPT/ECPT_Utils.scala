package ECPT.PTW

import chisel3._
import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
import chisel3.withClock
import chisel3.internal.sourceinfo.SourceInfo
import chipsalliance.rocketchip.config._
import ECPT.Params._
import freechips.rocketchip.rocket.PTE
import freechips.rocketchip.tile.CoreModule

/* -------------------- ECPT Utils -------------------- */

/**
 * Calculates the CRC for a stream of data.
 *
 * @param n The number of CRC bits to calculate.
 * @param g The generator polynomial.
 * @param data_len The length of input data
 */
class CRC_hash_FSM(n: Int, g: Long, data_len: Int)(implicit p: Parameters) extends MyCoreModule()(p) {
  val io = IO(new Bundle {
    /** Input */
    val start = Input(Bool())
    val data_in = Input(UInt(data_len.W))
    /** Output */
    val data_out = Output(UInt(n.W))
    val ready  = Output(Bool())
    val done = Output(Bool())
    /** Debug */
    val debug = Output(UInt(n.W))
  })


  /* Variable declaration */
  // Linear feedback shift register
  val lfsr = RegInit(VecInit((Seq.fill(n)(false.B))))
  /* state machine*/
  val s_ready :: s_compute :: s_done :: Nil = Enum(3)
  val state_reg = RegInit(s_ready)
  val next_state = WireDefault(state_reg)
  val lfsr_en = Wire(Bool())
  val crc_counter = Module(new CounterWithTrigger(data_len))
  val to_hash = WireDefault(0.U(data_len.W))
  // val to_hash = RegInit(0.U(data_len.W))

  println("----------------CRC_hash: -------------\n")
  println(s"The generator polynomial: ${g}\n")
  
  /* Control signals */
  val clear = WireDefault(false.B)
  clear := (state_reg === s_done)
  crc_counter.io.trigger := (state_reg === s_compute)

  /* Data Path */
  to_hash := io.data_in  
  val bitIn = to_hash((data_len - 1).U - crc_counter.io.count)  

  /*  
    * ==State Machine==
    * s_ready: ready to receive request 
    * s_compute: computing the CRC hash value
    * s_done: 
  */
  state_reg := next_state
  switch(state_reg){
    is(s_ready){
      next_state := Mux(io.start, s_compute, s_ready)
      // to_hash := io.data_in
    }
    is(s_compute){
      next_state := Mux(crc_counter.io.count === (data_len - 1).U, s_done, s_compute)
    }
    is(s_done){
      next_state := s_ready
      // to_hash := 0.U
    }
  }
  
  /* Linear Feedback Shift Register */
  lfsr_en := (state_reg === s_compute)
  // XOR the input bit with the last bit in the LFSR
  val bit = Mux(lfsr_en, bitIn ^ lfsr.last, 0.U)
  val sel_last = Mux(lfsr_en, lfsr.last, 0.U)

  // Load the first bit
  lfsr(0) := bit
  // Shift the LFSR bits
  for (i <- 0 until n - 1) {
    println(s"The ${i}th construct\n")
    if ((g & (1 << (i + 1))) != 0){
      println(s"between ${i+1} and ${i}\n")
      lfsr(i + 1) := lfsr(i) ^ sel_last 
    }
    else{
      lfsr(i + 1) := lfsr(i)
    }
  }

  /* Clearing content in lfsr when done */
  when(clear) {
    for (i <- 0 until n) {
      lfsr(i) := 0.U
    }
  }

  // Output
  io.data_out := Mux(state_reg === s_done, lfsr.asUInt, 0.U)
  io.debug := lfsr.asUInt
  io.ready := (state_reg === s_ready)
  io.done := (state_reg === s_done)

  // Debug
  // if (debug_flag) {
  //   printf("-------- CRC INFO START --------\n")
  //   printf("start: %d state_reg: %d next_state: %d crc_counter.io.trigger: %d crc_counter.io.counter: %d \n" +
  //           "to_hash: %d bitIn: %d io.data_in: %d io.data_out: %d lfsr_en: %d io.debug: %d\n" +
  //           "done: %d\n", 
  //         io.start, state_reg, next_state, crc_counter.io.trigger, crc_counter.io.count,
  //         to_hash, bitIn, io.data_in, io.data_out, lfsr_en, io.debug, 
  //         io.done)
  //   printf("-------- CRC INFO END --------\n")

  // }
  
}

/* 
  Counter controlled by trigger
 */
class CounterWithTrigger(n: Int) extends Module {
  val io = IO(new Bundle {
    val trigger = Input(Bool())
    val count = Output(UInt(log2Ceil(n).W))
  })

  // Define a 3-bit counter
  val counter = RegInit(0.U(log2Ceil(n).W))

  // Increment the counter when the trigger signal is asserted
  when(io.trigger) {
    when(counter === (n-1).U){
      counter := 0.U
    } .otherwise {
      counter := counter + 1.U
    }
  }

  // Output the counter value
  io.count := counter

}

/* 
 * ECPTE_CacheLine holds the whole cache_line in a register 
 * to get tag match for ECPT 
 */
class EC_PTE_CacheLine (implicit p : Parameters) extends MyCoreBundle()(p) {
  /* Contains a total of 8 PTE */
  val ptes = Vec(cacheBlockBytes/8, new PTE)

  def fetchTag4KB : UInt = {
    ptes.map(_.reserved_for_future(2,0)).reduce((a, b) => Cat(b, a))
    // .takeRight returns ordering msb to lsb, thus, cat need to follow original ordering
    // Cat(second12.pad(12), first15.pad(15))
  }
  def fetchTag2MB : UInt = {
    ptes.map(_.reserved_for_future(2,0)).reduce((b,a) => Cat(a,b))
  }
  def fetchTag1GB : UInt = {
    ptes.map(_.reserved_for_future(2,0)).reduce((b,a) => Cat(a,b))
  }

}
