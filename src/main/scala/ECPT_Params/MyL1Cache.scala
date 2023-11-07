package ECPT.Params

import freechips.rocketchip.tile._
import chisel3.util._

// trait L1CacheParams {
//   def nSets:         Int
//   def nWays:         Int
//   def rowBits:       Int
//   def nTLBSets:      Int
//   def nTLBWays:      Int
//   def blockBytes:    Int // TODO this is ignored in favor of p(CacheBlockBytes) in BaseTile
// }

trait MyHasL1CacheParameters extends MyHasTileParameters {
  val cacheParams: L1CacheParams

  def nSets = cacheParams.nSets
  def blockOffBits = lgCacheBlockBytes
  def idxBits = log2Up(cacheParams.nSets)
  def untagBits = blockOffBits + idxBits // 3 + 12 = 15
  def pgUntagBits = if (usingVM) untagBits min pgIdxBits else untagBits // 15 min 12
//   def tagBits = tlBundleParams.addressBits - pgUntagBits
  def tagBits = 32 - pgUntagBits // addressBits: 32
  def nWays = cacheParams.nWays
  def wayBits = log2Up(nWays)
  def isDM = nWays == 1
  def rowBits = cacheParams.rowBits
  def rowBytes = rowBits/8
  def rowOffBits = log2Up(rowBytes)
  def nTLBSets = cacheParams.nTLBSets
  def nTLBWays = cacheParams.nTLBWays

//   def cacheDataBits = tlBundleParams.dataBits
  def cacheDataBits = 64
  def cacheDataBytes = cacheDataBits / 8
  def cacheDataBeats = (cacheBlockBytes * 8) / cacheDataBits
  def refillCycles = cacheDataBeats
}