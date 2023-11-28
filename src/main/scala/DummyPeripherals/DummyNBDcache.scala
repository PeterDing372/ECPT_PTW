package DummyPeripherals

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tilelink.ClientMetadata
import freechips.rocketchip.tile.CoreModule


class DummyNBDcache(implicit p: Parameters) extends CoreModule with HasL1HellaCacheParameters {
  val io = IO(Flipped(new HellaCacheIO))

  // Data Array
  val dataArray = Module(new DataArray)
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
  val s1_resp = RegNext(io.req.fire)
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
    val writeMask = io.req.bits.mask

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


