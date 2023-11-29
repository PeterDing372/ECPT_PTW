package ECPT.Units


import chisel3._
import chisel3.util._
import ECPT.PTW.{EC_PTE_CacheLine, CounterWithTrigger}
import chipsalliance.rocketchip.config._
import freechips.rocketchip.tile.{CoreModule, CoreBundle}
import freechips.rocketchip.rocket.PTE

// import chisel3.util.{Arbiter, Cat, Decoupled, Enum, Mux1H, OHToUInt, PopCount, 
    //PriorityEncoder, PriorityEncoderOH, RegEnable, UIntToOH, Valid, is, isPow2, log2Ceil, switch}
class traverseLineDebugIO (implicit p : Parameters) extends CoreBundle()(p) {
  val state = Output(UInt(3.W))
  val cacheLine = Output(new EC_PTE_CacheLine)
  val tag = Output(UInt(27.W))
  val tagMatch = Output(Bool())

}


class traverseLine (implicit p : Parameters) extends CoreModule {
    val io = IO(new Bundle{
        val data_in = Flipped(Decoupled(UInt(64.W)))
        val start = Input(Bool())
        val debug = new traverseLineDebugIO
        val tag_in = Input(UInt(27.W))
    })
    val s_ready ::s_traverse :: s_done :: Nil = Enum(3)
    val state = RegInit(s_ready)
    val next_state = WireDefault(state)
    state := next_state

    val cacheLine = Reg(new EC_PTE_CacheLine)
    val lgCacheBlocks = log2Ceil(cacheBlockBytes/8)
    val traverse_count = Wire(UInt(lgCacheBlocks.W))
    println(s"lgCacheBlocks: ${lgCacheBlocks}, cacheBlockBytes ${cacheBlockBytes}")


    val counter = Module(new CounterWithTrigger(8))
    counter.io.trigger := io.data_in.valid && (state === s_traverse)
    traverse_count := counter.io.count

    when (io.data_in.valid) {
        cacheLine.ptes(traverse_count) := makePTE(io.data_in.bits)
    }

    switch (state) {
        is (s_ready) {
            next_state := Mux(io.start, s_traverse, s_ready)
        }
        is (s_traverse) {
            next_state := Mux(traverse_count === 7.U, s_done, s_traverse)
        }
        is (s_done) {
            next_state := s_ready

        }
    }
    val tag = Wire(UInt(27.W))
    tag := cacheLine.fetchTag4KB
    // printf("[traverseLine]: tag: %x\n", tag)

    /* Debug wiring */
    io.debug.cacheLine := cacheLine
    io.debug.state := state
    io.debug.tag := tag
    io.debug.tagMatch := (io.tag_in === tag)

    

    private def makePTE(bits: UInt) = {
        val tmp = bits.asTypeOf(new PTE())
        val pte = WireDefault(tmp)
        // pte := DontCare
        // pte.ppn := bits(43,0)
        // pte.reserved_for_future := bits(9, 0)
        pte
    }


}