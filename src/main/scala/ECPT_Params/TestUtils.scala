package ECPT_Test


//******************************************************************************
// Copyright (c) 2018 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------


import org.scalatest._

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import boom.common.BoomTileAttachParams
import freechips.rocketchip.rocket.HellaCacheIO
import chiseltest.ChiselScalatestTester
import org.scalatest.freespec.AnyFreeSpec
import scala.util.Random



/**
 * Factory object to help create a set of BOOM parameters to use in tests
 */
object RocketTestUtils {

  private def augment(tp: TileParams)(implicit p: Parameters): Parameters = p.alterPartial {
    case TileKey => tp
    case TileVisibilityNodeKey => new TLEdgeOut(TLClientPortParameters(Seq(TLClientParameters(
                                                                        name = "fake-client-node",
                                                                        sourceId = IdRange(0,2)))),
                                            TLManagerPortParameters(Seq(TLManagerParameters(
                                                                        address = Seq(
                                                                            AddressSet(x"8000_0000",
                                                                                    x"1000_0000" - 1)),
                                                                        supportsGet = TransferSizes(1, 64),
                                                                        supportsPutFull = TransferSizes(1, 64),
                                                                        supportsPutPartial = TransferSizes(1, 64))),
                                                                    8),
                                            Parameters.empty,
                                            null)

    case LookupByHartId => lookupByHartId(Seq(tp))
  }

  private def lookupByHartId(tps: Seq[TileParams]) = {
    // return a new lookup hart
    new LookupByHartIdImpl {
      def apply[T <: Data](f: TileParams => Option[T], hartId: UInt): T =
        PriorityMux(tps.collect { case t if f(t).isDefined => (t.hartId.U === hartId) -> f(t).get })
    }
  }

  def getParameters(configName: String, configPackage: String = "freechips.rocketchip.system"): Parameters = {
    // get the full path to the config
    val fullConfigName = configPackage + "." + configName

    // get the default unmodified params
    val origParams: Parameters = try {
      (Class.forName(fullConfigName).newInstance.asInstanceOf[Config] ++ Parameters.empty)
    }
    catch {
      case e: java.lang.ClassNotFoundException =>
        throw new Exception(s"""Unable to find config "$fullConfigName".""", e)
    }

    // get the tile parameters
    val rocketTileParams = origParams(TilesLocated(InSubsystem)).collect { case n: RocketTileAttachParams => n }.map(_.tileParams)
    // rocketTileParams.
    // augment the parameters
    val outParams = augment(rocketTileParams.head)(origParams)

    outParams
  }
}


/**
 * Factory object to help create a set of BOOM parameters to use in tests
 */
object BoomTestUtils {

  private def augment(tp: TileParams)(implicit p: Parameters): Parameters = p.alterPartial {
    case TileKey => tp
    case TileVisibilityNodeKey => new TLEdgeOut(TLClientPortParameters(Seq(TLClientParameters(
                                                                        name = "fake-client-node",
                                                                        sourceId = IdRange(0,2)))),
                                            TLManagerPortParameters(Seq(TLManagerParameters(
                                                                        address = Seq(
                                                                            AddressSet(x"8000_0000",
                                                                                    x"1000_0000" - 1)),
                                                                        supportsGet = TransferSizes(1, 64),
                                                                        supportsPutFull = TransferSizes(1, 64),
                                                                        supportsPutPartial = TransferSizes(1, 64))),
                                                                    8),
                                            Parameters.empty,
                                            null)

    case LookupByHartId => lookupByHartId(Seq(tp))
  }

  private def lookupByHartId(tps: Seq[TileParams]) = {
    // return a new lookup hart
    new LookupByHartIdImpl {
      def apply[T <: Data](f: TileParams => Option[T], hartId: UInt): T =
        PriorityMux(tps.collect { case t if f(t).isDefined => (t.hartId.U === hartId) -> f(t).get })
    }
  }

  def getParameters(configName: String, configPackage: String = "ECPT_Params"): Parameters = {
    // get the full path to the config
    val fullConfigName = configPackage + "." + configName

    // get the default unmodified params
    val origParams: Parameters = try {
      (Class.forName(fullConfigName).newInstance.asInstanceOf[Config] ++ Parameters.empty)
    }
    catch {
      case e: java.lang.ClassNotFoundException =>
        throw new Exception(s"""Unable to find config "$fullConfigName".""", e)
    }

    // get the tile parameters
    val BoomTileParams = origParams(TilesLocated(InSubsystem)).collect { case n: BoomTileAttachParams => n }.map(_.tileParams)

    // augment the parameters
    val outParams = augment(BoomTileParams.head)(origParams)

    outParams
  }
}


object ECPTTestUtils {
  /**
   * Format an 64 bit PTE base on the given inputs
   * @param reserved_for_future Long 
   * @param ppn Long - physical page number
   * @param x Long - execute permission
   * @param w Long - write permission
   * @param r Long - read permission
   *  
  */
  def formatPTE(reserved_for_future: Long,
                  ppn: Long,
                  reserved_for_software: Long = 0,
                  d: Long = 0,
                  a: Long = 0,
                  g: Long = 0,
                  u: Long = 0,
                  x: Long = 0,
                  w: Long = 0,
                  r: Long = 1,
                  v: Long = 0
                  ): UInt = {
      // var r_pte = UInt(64.W)
      val r_pte = ((reserved_for_future << 54) | 
                 (ppn << 10) | 
                 (reserved_for_software << 8) | 
                 (d << 7) | 
                 (a << 6) | 
                 (g << 5) | 
                 (u << 4) | 
                 (x << 3) | 
                 (w << 2) | 
                 (r << 1) | 
                 v)
      // println(s"[formatPTE] r_pte ${r_pte.toBinaryString}")
      r_pte.U // & ~0.U(64.W)
    }


    def formRadixReqAddr(vpn2 : Int, vpn1 : Int, vpn0 : Int): Int = {
        // this form SV39 scheme VPN
        require(vpn2 < (1<<9) && vpn1 < (1<<9) && vpn0 < (1<<9))
        val finalVPN = (vpn2 << 18) | (vpn1 << 9) | vpn0
        finalVPN
    }


    def AddrToRespGroup(value: UInt): Array[Int] = {
      val bits27 = value(26, 0)  // Ensure the UInt is 27 bits wide
      val groups = new Array[Int](8)

      // Extracting 8 groups of 3 bits each, ignoring the lower 3 bits
      for (i <- 0 until 8) {
          groups(i) = bits27(3 * i + 5, 3 * i + 3).litValue.toInt
      }
      groups
  }

    def generateRandomBinaryString(): String = {
        // Function to generate a random binary string of a given length
        def randomBinaryGroup(length: Int): String = {
            Seq.fill(length)(Random.nextInt(2)).mkString
        }

        // Generating random groups
        val lsbGroups = (1 to 5).map(_ => randomBinaryGroup(3)).mkString("_")
        val msbGroups = (1 to 3).map(_ => randomBinaryGroup(4)).mkString("_")

        // Concatenating all parts
        "b" + msbGroups + "_" + lsbGroups
    }


    
}

