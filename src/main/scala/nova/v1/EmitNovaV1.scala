package nova.v1

import circt.stage.ChiselStage

object EmitNovaV1 {
  def main(args: Array[String]): Unit = {
    val targetDir = args.sliding(2, 1).collectFirst { case Array("--target-dir", dir) => dir }.getOrElse("generated")
    ChiselStage.emitSystemVerilogFile(
      new NovaV1(NovaV1Params(bootPc = 0x00000000L, hartId = 0, enableTrace = true, startHalted = true)),
      Array("--target-dir", targetDir, "--split-verilog"),
      Array("--disable-all-randomization", "--strip-debug-info")
    )
  }
}
