package ECPT.DummmyPeriphrals


import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import freechips.rocketchip.system.DefaultConfig
import freechips.rocketchip.system._
import ECPT.Params._
import chipsalliance.rocketchip.config._
import ECPT_Test._


class DummyCSRSpec extends AnyFreeSpec with ChiselScalatestTester{
    val boomParams: Parameters = RocketTestUtils.getParameters("DefaultConfig")
    implicit val para: Parameters = boomParams


    "DummyCSR should Compile" in {
        test(new DummyCSR()(para) ) { c =>
            println("SIMULATION: testing DummyCSR")
            
        }

    }

    
}



