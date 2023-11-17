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

// import boom.system._

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

  def getRocketParameters(configName: String, configPackage: String = "freechips.rocketchip.system"): Parameters = {
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
