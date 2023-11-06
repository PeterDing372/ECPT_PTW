package ECPT.PTW

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.system.DefaultConfig


case object DEBUG_FLAG extends Field[Boolean](false)

class myConfig(val debug: Boolean) extends Config(
    new DefaultConfig ++
    new DebugFlagConfig(debug)
)

class DebugFlagConfig(val debug: Boolean) extends Config ((site, here, up) => {
    case DEBUG_FLAG => debug
})

