package nova.v1

import chisel3._
import chisel3.util._

final class NovaV1IO(params: NovaV1Params) extends Bundle {
  val mem = new NovaV1MemoryPorts(params.axiIdWidth)
  val reg = new NovaV1RegisterPorts
  val platform = Input(new NovaV1PlatformInputs)
  val debug = new NovaV1DebugPorts
}
