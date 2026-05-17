package nova.v1

import circt.stage.ChiselStage
import org.scalatest.funsuite.AnyFunSuite

final class NovaV1ElaborationSpec extends AnyFunSuite {
  test("NovaV1 elaborates to SystemVerilog with the required external ports") {
    val sv = ChiselStage.emitSystemVerilog(
      new NovaV1(NovaV1Params(bootPc = 0x80L, hartId = 0, enableTrace = true, startHalted = true))
    )

    assert(sv.contains("module NovaV1"))
    Seq(
      "mem_instruction_ar_bits_id",
      "mem_instruction_r_bits_id",
      "mem_data_ar_bits_id",
      "mem_data_aw_bits_cache",
      "mem_data_b_bits_id",
      "reg_control_aw_bits_addr",
      "reg_control_r_valid",
      "platform_mtime",
      "platform_irq_machineTimer",
      "debug_hart_currentPc",
      "debug_hart_traceBackpressured",
      "debug_retire_valid",
      "debug_retire_ready",
      "debug_retire_bits_pc"
    ).foreach { port =>
      assert(sv.contains(port), s"missing expected port fragment $port")
    }
  }

  test("architectural constants match the NovaV1 plan") {
    assert(NovaConstants.MisaValue.litValue == BigInt("40001107", 16))
    assert(NovaAtomicOp.maxu.litValue == 11)
  }

  test("retire trace port remains present when tracing is disabled") {
    val sv = ChiselStage.emitSystemVerilog(
      new NovaV1(NovaV1Params(bootPc = 0x80L, hartId = 0, enableTrace = false, startHalted = true))
    )

    assert(sv.contains("debug_retire_valid"))
    assert(sv.contains("debug_retire_bits_pc"))
  }
}
