package DummyPeripherals

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tilelink.ClientMetadata
import freechips.rocketchip.tile.CoreModule
import freechips.rocketchip.util.DescribedSRAM


class DummyNBDcache(implicit p: Parameters) extends CoreModule with HasL1HellaCacheParameters {
  val io = IO(Flipped(new HellaCacheIO))
  println(s"[DummyNBDcache Parameters] rowWords: ${rowWords} nWays: ${nWays} " +
    s"nSets ${nSets}")
  override def nWays: Int = 1
  // Data Array
  val dataArray = Module(new MyDataArray)
  // val readArb = Module(new Arbiter(new L1DataReadReq, 4))
  // val writeArb = Module(new Arbiter(new L1DataWriteReq, 1))
  



  // Metadata Array
  def onReset = L1Metadata(0.U, ClientMetadata.onReset)
  val metadataArray = Module(new L1MetadataArray(onReset _))

  // Connecting read ports of data array and metadata array to the CPU request
  dataArray.io.read.valid := io.req.valid
  dataArray.io.read.bits.addr := io.req.bits.addr
  dataArray.io.read.bits.way_en := ~0.U(nWays.W) // Assume all ways are valid for simplicity

  metadataArray.io.read.valid := io.req.valid
  metadataArray.io.read.bits.idx := io.req.bits.addr >> blockOffBits
  metadataArray.io.read.bits.way_en := ~0.U(nWays.W)
  metadataArray.io.read.bits.tag := io.req.bits.addr >> untagBits

  // Handling cache responses
  val s1_resp = RegNext(io.req.valid)
  val s1_req = RegEnable(io.req.bits, io.req.fire)

  val readData = dataArray.io.resp
  val readMeta = metadataArray.io.resp

  // Simple way selection (for example, select the first way)
  val selectedData = readData(0)
  val selectedMeta = readMeta(0)

  // Check if tag matches and data is valid
  val hit = selectedMeta.coh.isValid() && (selectedMeta.tag === (s1_req.addr >> untagBits))
  
  // Load data generation
  val loadedData = selectedData // Assuming no ECC, no subword selection

  // Response to the CPU
  io.resp.valid := s1_resp && hit
  io.resp.bits := DontCare
  io.resp.bits.data := loadedData
  io.resp.bits.addr := s1_req.addr
  io.resp.bits.cmd := s1_req.cmd
  io.resp.bits.size := s1_req.size
  io.resp.bits.has_data := isRead(s1_req.cmd)

  // Write logic (default, over-written when isWrite)
  dataArray.io.write.valid := false.B
  dataArray.io.write.bits := DontCare
  metadataArray.io.write.valid := false.B
  metadataArray.io.write.bits := DontCare


  // Write logic
  when(io.req.valid && isWrite(io.req.bits.cmd)) {
    // Assuming a simple write-through policy
    val writeWay = 0.U // For simplicity, assume we always write to way 0
    val writeData = io.req.bits.data
    val writeAddr = io.req.bits.addr
    val writeMask = ~0.U(rowWords.W)// io.req.bits.mask

    // Update Data Array
    dataArray.io.write.valid := true.B
    dataArray.io.write.bits.addr := writeAddr
    dataArray.io.write.bits.data := writeData
    dataArray.io.write.bits.way_en := UIntToOH(writeWay)
    dataArray.io.write.bits.wmask := writeMask

    // Update Metadata Array
    metadataArray.io.write.valid := true.B
    metadataArray.io.write.bits.idx := writeAddr >> blockOffBits
    metadataArray.io.write.bits.tag := writeAddr >> untagBits
    metadataArray.io.write.bits.way_en := UIntToOH(writeWay)
    metadataArray.io.write.bits.data.coh := ClientMetadata.onReset // Set valid state
    metadataArray.io.write.bits.data.tag := writeAddr >> untagBits
  }

  // Handling misses and other features are not implemented for simplicity
  // Normally, here you would handle cache refills and write-backs
  // Handling signals not used in this simplified cache
  io.s1_kill := DontCare
  io.s1_data := DontCare
  io.s2_nack := DontCare
  io.s2_nack_cause_raw := DontCare
  io.s2_kill := DontCare
  io.s2_uncached := DontCare
  io.s2_paddr := DontCare
  io.replay_next := DontCare
  io.s2_xcpt := DontCare
  io.s2_gpa := DontCare
  io.s2_gpa_is_pte := DontCare
  io.uncached_resp.foreach(_ := DontCare)
  io.ordered := DontCare
  io.perf := DontCare
  io.keep_clock_enabled := DontCare
  io.clock_enabled := DontCare
}


class MyDataArray(implicit p: Parameters) extends L1HellaCacheModule()(p) {

  val io = IO(new Bundle {
    val read = Flipped(Decoupled(new L1DataReadReq))
    val write = Flipped(Decoupled(new L1DataWriteReq))
    val resp = Output(Vec(nWays, Bits(encRowBits.W)))
  })
  override def nWays: Int = 1
  println(s"[MyDataArray Parameters] rowWords: ${rowWords} nWays: ${nWays} " +
    s"rowBits: ${rowBits} doNarrowRead: ${doNarrowRead}\n" +
    s"refillCycles: ${refillCycles} cacheDataBytes: ${cacheDataBytes} " +
    s"encRowBits: ${encRowBits}")



  val waddr = io.write.bits.addr >> rowOffBits
  val raddr = io.read.bits.addr >> rowOffBits

  if (doNarrowRead) {
    for (w <- 0 until nWays by rowWords) {
      // get the current way enable
      val wway_en = io.write.bits.way_en(w+rowWords-1,w)
      val rway_en = io.read.bits.way_en(w+rowWords-1,w)

      val resp = Wire(Vec(rowWords, Bits(encRowBits.W)))
      val r_raddr = RegEnable(io.read.bits.addr, io.read.valid)
      for (i <- 0 until resp.size) { // size = 1
        val array  = DescribedSRAM(
          name = s"array_${w}_${i}",
          desc = "Non-blocking DCache Data Array",
          size = nSets * refillCycles, 
          // multiply refillCycles is just another calculation for cache line size
          data = Vec(rowWords, Bits(encDataBits.W))
        )
        when (wway_en.orR && io.write.valid && io.write.bits.wmask(i)) {
          val data = VecInit.fill(rowWords)(io.write.bits.data(encDataBits*(i+1)-1,encDataBits*i))
          array.write(waddr, data, wway_en.asBools)
        }
        resp(i) := array.read(raddr, rway_en.orR && io.read.valid).asUInt
      }
      for (dw <- 0 until rowWords) {
        val r = VecInit(resp.map(_(encDataBits*(dw+1)-1,encDataBits*dw)))
        val resp_mux =
          if (r.size == 1) r
          else VecInit(r(r_raddr(rowOffBits-1,wordOffBits)), r.tail:_*)
        io.resp(w+dw) := resp_mux.asUInt
      }
    }
  } else {
    for (w <- 0 until nWays) {
      val array  = DescribedSRAM(
        name = s"array_${w}",
        desc = "Non-blocking DCache Data Array",
        size = nSets * refillCycles,
        data = Vec(rowWords, Bits(encDataBits.W))
      )
      when (io.write.bits.way_en(w) && io.write.valid) {
        val data = VecInit.tabulate(rowWords)(i => io.write.bits.data(encDataBits*(i+1)-1,encDataBits*i))
        array.write(waddr, data, io.write.bits.wmask.asBools)
      }
      io.resp(w) := array.read(raddr, io.read.bits.way_en(w) && io.read.valid).asUInt
    }
  }

  io.read.ready := true.B
  io.write.ready := true.B
}



class MyL1MetadataArray[T <: L1Metadata](onReset: () => T)(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  override def nWays: Int = 1
  println(s"[MyL1MetadataArray Parameters] rowWords: ${rowWords} nWays: ${nWays}")
  val rstVal = onReset()
  val io = IO(new Bundle {
    val read = Flipped(Decoupled(new L1MetaReadReq))
    val write = Flipped(Decoupled(new L1MetaWriteReq))
    val resp = Output(Vec(nWays, rstVal.cloneType))
  })

  val rst_cnt = RegInit(0.U(log2Up(nSets+1).W))
  val rst = rst_cnt < nSets.U
  val waddr = Mux(rst, rst_cnt, io.write.bits.idx)
  val wdata = Mux(rst, rstVal, io.write.bits.data).asUInt
  val wmask = Mux(rst || (nWays == 1).B, (-1).S, io.write.bits.way_en.asSInt).asBools
  val rmask = Mux(rst || (nWays == 1).B, (-1).S, io.read.bits.way_en.asSInt).asBools
  when (rst) { rst_cnt := rst_cnt+1.U }

  val metabits = rstVal.getWidth
  val tag_array = SyncReadMem(nSets, Vec(nWays, UInt(metabits.W)))
  val wen = rst || io.write.valid
  when (wen) {
    tag_array.write(waddr, VecInit.fill(nWays)(wdata), wmask)
  }
  io.resp := tag_array.read(io.read.bits.idx, io.read.fire()).map(_.asTypeOf(chiselTypeOf(rstVal)))

  io.read.ready := !wen // so really this could be a 6T RAM
  io.write.ready := !rst
}



