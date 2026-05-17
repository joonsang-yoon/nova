package nova.v1

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

final class NovaV1ExecutionSpec extends AnyFunSuite with ChiselSim {
  import NovaV1ExecutionSpec._

  test("AXI-backed RV32I/M/Zba/Zbb/Zbs/CSR program retires expected architectural trace") {
    simulate(new NovaV1(NovaV1Params(bootPc = 0x80L, hartId = 0, enableTrace = true, startHalted = false))) { dut =>
      val mem = new ZeroWaitMemory
      val axi = new ZeroWaitAxi(dut, mem)
      val start = 0x80L
      val target = 0xdcL
      val words = Seq(
        addi(1, 0, 5),
        addi(2, 0, 10),
        branch(1, 2, 1, 8),
        addi(31, 0, 99),
        addi(20, 0, 7),
        op(3, 1, 2, 0, 0),
        op(4, 2, 1, 0, 0x20),
        opImmShift(5, 1, 3, 1, 0),
        op(6, 4, 5, 0, 1),
        op(7, 6, 1, 4, 1),
        op(8, 6, 2, 6, 1),
        op(9, 1, 2, 2, 0x10),
        op(10, 1, 2, 4, 0x05),
        op(11, 1, 2, 7, 0x05),
        opImmShift(12, 1, 4, 1, 0x14),
        opImmShift(13, 12, 4, 5, 0x24),
        opImmShift(14, 12, 0, 1, 0x24),
        opImmShift(15, 14, 1, 1, 0x34),
        opImmShift(16, 12, 24, 5, 0x34),
        csr(17, 0, 0x301, 2),
        addi(21, 0, (target + 1).toInt),
        jalr(22, 21, 0),
        addi(23, 0, 99),
        addi(23, 0, 7),
        fenceI
      )
      mem.loadWords(start, words)

      Seq(
        Expected(0x80, words(0), nextPc = 0x84, rd = Some(1 -> 5)),
        Expected(0x84, words(1), nextPc = 0x88, rd = Some(2 -> 10)),
        Expected(0x88, words(2), nextPc = 0x90),
        Expected(0x90, words(4), nextPc = 0x94, rd = Some(20 -> 7)),
        Expected(0x94, words(5), nextPc = 0x98, rd = Some(3 -> 15)),
        Expected(0x98, words(6), nextPc = 0x9c, rd = Some(4 -> 5)),
        Expected(0x9c, words(7), nextPc = 0xa0, rd = Some(5 -> 40)),
        Expected(0xa0, words(8), nextPc = 0xa4, rd = Some(6 -> 200)),
        Expected(0xa4, words(9), nextPc = 0xa8, rd = Some(7 -> 40)),
        Expected(0xa8, words(10), nextPc = 0xac, rd = Some(8 -> 0)),
        Expected(0xac, words(11), nextPc = 0xb0, rd = Some(9 -> 20)),
        Expected(0xb0, words(12), nextPc = 0xb4, rd = Some(10 -> 5)),
        Expected(0xb4, words(13), nextPc = 0xb8, rd = Some(11 -> 10)),
        Expected(0xb8, words(14), nextPc = 0xbc, rd = Some(12 -> 21)),
        Expected(0xbc, words(15), nextPc = 0xc0, rd = Some(13 -> 1)),
        Expected(0xc0, words(16), nextPc = 0xc4, rd = Some(14 -> 20)),
        Expected(0xc4, words(17), nextPc = 0xc8, rd = Some(15 -> 22)),
        Expected(0xc8, words(18), nextPc = 0xcc, rd = Some(16 -> 0x15000000L)),
        Expected(0xcc, words(19), nextPc = 0xd0, rd = Some(17 -> 0x40001107L), csr = Some(0x301)),
        Expected(0xd0, words(20), nextPc = 0xd4, rd = Some(21 -> (target + 1))),
        Expected(0xd4, words(21), nextPc = target, rd = Some(22 -> 0xd8)),
        Expected(target, words(23), nextPc = 0xe0, rd = Some(23 -> 7)),
        Expected(0xe0, words(24), nextPc = 0xe4)
      ).foreach { exp =>
        checkTrace(axi.stepUntilTrace(), exp)
      }
    }
  }

  test("AXI LSU exposes byte lanes, atomics, x0 load side effects, and misalignment traps") {
    simulate(new NovaV1(NovaV1Params(bootPc = 0x200L, hartId = 0, enableTrace = true, startHalted = false))) { dut =>
      val mem = new ZeroWaitMemory
      val axi = new ZeroWaitAxi(dut, mem)
      val start = 0x200L
      val words = Seq(
        lui(1, 1),
        addi(2, 0, 0x12),
        store(2, 1, 0, 0),
        addi(3, 0, 0x345),
        store(3, 1, 2, 1),
        load(4, 1, 0, 2),
        load(5, 1, 2, 0),
        load(6, 1, 2, 5),
        addi(8, 0, -1),
        store(8, 1, 8, 2),
        addi(7, 1, 8),
        amo(9, 7, 0, 0x02),
        addi(10, 0, 0x55),
        amo(11, 7, 10, 0x03),
        load(12, 1, 8, 2),
        amo(13, 7, 8, 0x01),
        load(14, 1, 8, 2),
        amo(15, 7, 10, 0x00),
        load(16, 1, 8, 2),
        load(0, 1, 8, 2),
        load(17, 1, 1, 2)
      )
      mem.loadWords(start, words)

      Seq(
        Expected(0x200, words(0), nextPc = 0x204, rd = Some(1 -> 0x1000)),
        Expected(0x204, words(1), nextPc = 0x208, rd = Some(2 -> 0x12)),
        Expected(0x208, words(2), nextPc = 0x20c, mem = Some(MemExpectation(0x1000, write = true, size = 0, wdata = 0x12))),
        Expected(0x20c, words(3), nextPc = 0x210, rd = Some(3 -> 0x345)),
        Expected(0x210, words(4), nextPc = 0x214, mem = Some(MemExpectation(0x1002, write = true, size = 1, wdata = 0x03450000L))),
        Expected(0x214, words(5), nextPc = 0x218, rd = Some(4 -> 0x03450012L), mem = Some(MemExpectation(0x1000, write = false, size = 2, rdata = 0x03450012L))),
        Expected(0x218, words(6), nextPc = 0x21c, rd = Some(5 -> 0x45), mem = Some(MemExpectation(0x1002, write = false, size = 0, rdata = 0x03450012L))),
        Expected(0x21c, words(7), nextPc = 0x220, rd = Some(6 -> 0x345), mem = Some(MemExpectation(0x1002, write = false, size = 1, rdata = 0x03450012L))),
        Expected(0x220, words(8), nextPc = 0x224, rd = Some(8 -> 0xffffffffL)),
        Expected(0x224, words(9), nextPc = 0x228, mem = Some(MemExpectation(0x1008, write = true, size = 2, wdata = 0xffffffffL))),
        Expected(0x228, words(10), nextPc = 0x22c, rd = Some(7 -> 0x1008)),
        Expected(0x22c, words(11), nextPc = 0x230, rd = Some(9 -> 0xffffffffL), mem = Some(MemExpectation(0x1008, write = false, size = 2, rdata = 0xffffffffL, atomic = 1))),
        Expected(0x230, words(12), nextPc = 0x234, rd = Some(10 -> 0x55)),
        Expected(0x234, words(13), nextPc = 0x238, rd = Some(11 -> 0), mem = Some(MemExpectation(0x1008, write = true, size = 2, wdata = 0x55, rdata = 0, atomic = 2))),
        Expected(0x238, words(14), nextPc = 0x23c, rd = Some(12 -> 0x55), mem = Some(MemExpectation(0x1008, write = false, size = 2, rdata = 0x55))),
        Expected(0x23c, words(15), nextPc = 0x240, rd = Some(13 -> 0x55), mem = Some(MemExpectation(0x1008, write = true, size = 2, wdata = 0xffffffffL, rdata = 0x55, atomic = 3))),
        Expected(0x240, words(16), nextPc = 0x244, rd = Some(14 -> 0xffffffffL), mem = Some(MemExpectation(0x1008, write = false, size = 2, rdata = 0xffffffffL))),
        Expected(0x244, words(17), nextPc = 0x248, rd = Some(15 -> 0xffffffffL), mem = Some(MemExpectation(0x1008, write = true, size = 2, wdata = 0x54, rdata = 0xffffffffL, atomic = 4))),
        Expected(0x248, words(18), nextPc = 0x24c, rd = Some(16 -> 0x54), mem = Some(MemExpectation(0x1008, write = false, size = 2, rdata = 0x54))),
        Expected(0x24c, words(19), nextPc = 0x250, mem = Some(MemExpectation(0x1008, write = false, size = 2, rdata = 0x54))),
        Expected(0x250, words(20), nextPc = 0, retired = false, trap = Some(BigInt(4) -> BigInt(0x1001)))
      ).foreach { exp =>
        checkTrace(axi.stepUntilTrace(), exp)
      }
      assert(mem.readWord(0x1008) == 0x54)
    }
  }

  test("compressed and halfword-crossing instruction fetches assemble through aligned AXI reads") {
    simulate(new NovaV1(NovaV1Params(bootPc = 0x300L, hartId = 0, enableTrace = true, startHalted = false))) { dut =>
      val mem = new ZeroWaitMemory
      val axi = new ZeroWaitAxi(dut, mem)
      val start = 0x300L
      val splitAddi = addi(4, 0, 42)
      val parcels = Seq(
        0x0001,
        (splitAddi & 0xffff).toInt,
        ((splitAddi >> 16) & 0xffff).toInt,
        0x0085,
        0x0000)
      mem.loadHalfWords(start, parcels)

      Seq(
        Expected(0x300, 0x0001, len = 2, nextPc = 0x302),
        Expected(0x302, splitAddi, len = 4, nextPc = 0x306, rd = Some(4 -> 42)),
        Expected(0x306, 0x0085, len = 2, nextPc = 0x308, rd = Some(1 -> 1)),
        Expected(0x308, 0x0000, len = 2, nextPc = 0, retired = false, trap = Some(BigInt(2) -> BigInt(0)))
      ).foreach { exp =>
        checkTrace(axi.stepUntilTrace(), exp)
      }
    }
  }

  test("Decoupled debug retire buffers one event and stalls retirement while backpressured") {
    simulate(new NovaV1(NovaV1Params(bootPc = 0x500L, hartId = 0, enableTrace = true, startHalted = false))) { dut =>
      val mem = new ZeroWaitMemory
      val axi = new ZeroWaitAxi(dut, mem)
      val words = Seq(addi(1, 0, 11), addi(2, 0, 22))
      mem.loadWords(0x500, words)

      axi.setTraceReady(false)
      axi.step(24)
      assert(dut.io.debug.retire.valid.peek().litToBoolean)
      assert(dut.io.debug.retire.bits.pc.peek().litValue == BigInt(0x500))
      assert(dut.io.debug.hart.traceBackpressured.peek().litToBoolean)

      axi.setTraceReady(true)
      checkTrace(axi.stepUntilTrace(), Expected(0x500, words(0), nextPc = 0x504, rd = Some(1 -> 11)))
      checkTrace(axi.stepUntilTrace(), Expected(0x504, words(1), nextPc = 0x508, rd = Some(2 -> 22)))
      assert(!dut.io.debug.hart.traceBackpressured.peek().litToBoolean)
    }
  }

  test("AXI4-Lite control registers set PC, resume, halt, and report debug hart status") {
    simulate(new NovaV1(NovaV1Params(bootPc = 0, hartId = 3, enableTrace = true, startHalted = true))) { dut =>
      val mem = new ZeroWaitMemory
      val axi = new ZeroWaitAxi(dut, mem)
      val inst = addi(1, 0, 42)
      mem.loadWords(0x400, Seq(inst))

      axi.controlRead(NovaV1ControlMap.HART_ID, expected = 3)
      axi.controlWrite(NovaV1ControlMap.BOOT_PC, 0x400)
      axi.controlWrite(NovaV1ControlMap.COMMAND, 0x0c)
      checkTrace(axi.stepUntilTrace(), Expected(0x400, inst, nextPc = 0x404, rd = Some(1 -> 42)))
      axi.controlWrite(NovaV1ControlMap.COMMAND, 0x02)
      axi.step(20)
      assert(dut.io.debug.hart.halted.peek().litToBoolean)
      axi.controlRead(NovaV1ControlMap.CORE_STATUS, expectedMask = Some(BigInt(0x2) -> BigInt(0x2)))
    }
  }
}

object NovaV1ExecutionSpec extends chisel3.simulator.PeekPokeAPI {
  private val U32 = BigInt("ffffffff", 16)

  private final case class MemExpectation(
      addr: Long,
      write: Boolean,
      size: Int,
      wdata: BigInt = 0,
      rdata: BigInt = 0,
      atomic: Int = 0)

  private final case class Expected(
      pc: Long,
      instr: BigInt,
      len: Int = 4,
      nextPc: Long,
      rd: Option[(Int, BigInt)] = None,
      retired: Boolean = true,
      trap: Option[(BigInt, BigInt)] = None,
      mem: Option[MemExpectation] = None,
      csr: Option[Int] = None)

  private final case class TraceRecord(
      retired: Boolean,
      pc: BigInt,
      instr: BigInt,
      instrLen: BigInt,
      nextPc: BigInt,
      rd: BigInt,
      rdWen: Boolean,
      rdValue: BigInt,
      trap: Boolean,
      trapCause: BigInt,
      trapValue: BigInt,
      memValid: Boolean,
      memWrite: Boolean,
      memAddr: BigInt,
      memSize: BigInt,
      memWdata: BigInt,
      memRdata: BigInt,
      memAtomic: BigInt,
      csrValid: Boolean,
      csrAddr: BigInt)

  private final class ZeroWaitAxi(dut: NovaV1, mem: ZeroWaitMemory) {
    private var iResp: Option[(BigInt, Int, BigInt)] = None
    private var dResp: Option[(BigInt, Int, BigInt)] = None
    private var bResp: Option[(Int, BigInt)] = None
    private var aw: Option[(Long, Boolean, BigInt)] = None
    private var w: Option[(BigInt, Int)] = None
    private var traceReady = true

    def setTraceReady(value: Boolean): Unit = {
      traceReady = value
    }

    def stepUntilTrace(maxCycles: Int = 200): TraceRecord = {
      for (_ <- 0 until maxCycles) {
        driveDefaults()
        if (dut.io.debug.retire.valid.peek().litToBoolean) {
          val out = peekTrace(dut)
          advance()
          return out
        }
        advance()
      }
      throw new AssertionError(s"timed out waiting for trace at pc=0x${dut.io.debug.hart.currentPc.peek().litValue.toString(16)}")
    }

    def step(cycles: Int): Unit =
      (0 until cycles).foreach { _ =>
        driveDefaults()
        advance()
      }

    def controlWrite(addr: Int, data: BigInt, expectedResp: Int = 0): Unit = {
      driveDefaults()
      dut.io.reg.control.aw.valid.poke(true.B)
      dut.io.reg.control.aw.bits.addr.poke(addr)
      dut.io.reg.control.aw.bits.prot.poke(0)
      dut.io.reg.control.w.valid.poke(true.B)
      dut.io.reg.control.w.bits.data.poke(data & U32)
      dut.io.reg.control.w.bits.strb.poke(0xf)
      advance()
      for (_ <- 0 until 8) {
        driveDefaults()
        if (dut.io.reg.control.b.valid.peek().litToBoolean) {
          assert(dut.io.reg.control.b.bits.resp.peek().litValue == BigInt(expectedResp))
          advance()
          return
        }
        advance()
      }
      throw new AssertionError("timed out waiting for AXI-Lite write response")
    }

    def controlRead(addr: Int, expected: BigInt = 0, expectedResp: Int = 0, expectedMask: Option[(BigInt, BigInt)] = None): Unit = {
      driveDefaults()
      dut.io.reg.control.ar.valid.poke(true.B)
      dut.io.reg.control.ar.bits.addr.poke(addr)
      dut.io.reg.control.ar.bits.prot.poke(0)
      advance()
      for (_ <- 0 until 8) {
        driveDefaults()
        if (dut.io.reg.control.r.valid.peek().litToBoolean) {
          val data = dut.io.reg.control.r.bits.data.peek().litValue
          assert(dut.io.reg.control.r.bits.resp.peek().litValue == BigInt(expectedResp))
          expectedMask match {
            case Some((mask, value)) => assert((data & mask) == value)
            case None => assert(data == expected)
          }
          advance()
          return
        }
        advance()
      }
      throw new AssertionError("timed out waiting for AXI-Lite read response")
    }

    private def driveDefaults(): Unit = {
      dut.io.mem.instruction.ar.ready.poke(true.B)
      dut.io.mem.instruction.r.valid.poke(iResp.isDefined.B)
      dut.io.mem.instruction.r.bits.id.poke(iResp.map(_._3).getOrElse(BigInt(0)))
      dut.io.mem.instruction.r.bits.data.poke(iResp.map(_._1).getOrElse(BigInt(0)) & U32)
      dut.io.mem.instruction.r.bits.resp.poke(iResp.map(_._2).getOrElse(0))
      dut.io.mem.instruction.r.bits.last.poke(true.B)

      dut.io.mem.data.ar.ready.poke(true.B)
      dut.io.mem.data.r.valid.poke(dResp.isDefined.B)
      dut.io.mem.data.r.bits.id.poke(dResp.map(_._3).getOrElse(BigInt(0)))
      dut.io.mem.data.r.bits.data.poke(dResp.map(_._1).getOrElse(BigInt(0)) & U32)
      dut.io.mem.data.r.bits.resp.poke(dResp.map(_._2).getOrElse(0))
      dut.io.mem.data.r.bits.last.poke(true.B)
      dut.io.mem.data.aw.ready.poke(true.B)
      dut.io.mem.data.w.ready.poke(true.B)
      dut.io.mem.data.b.valid.poke(bResp.isDefined.B)
      dut.io.mem.data.b.bits.id.poke(bResp.map(_._2).getOrElse(BigInt(0)))
      dut.io.mem.data.b.bits.resp.poke(bResp.map(_._1).getOrElse(0))

      dut.io.reg.control.aw.valid.poke(false.B)
      dut.io.reg.control.aw.bits.addr.poke(0)
      dut.io.reg.control.aw.bits.prot.poke(0)
      dut.io.reg.control.w.valid.poke(false.B)
      dut.io.reg.control.w.bits.data.poke(0)
      dut.io.reg.control.w.bits.strb.poke(0)
      dut.io.reg.control.b.ready.poke(true.B)
      dut.io.reg.control.ar.valid.poke(false.B)
      dut.io.reg.control.ar.bits.addr.poke(0)
      dut.io.reg.control.ar.bits.prot.poke(0)
      dut.io.reg.control.r.ready.poke(true.B)

      dut.io.platform.mtime.poke(0)
      dut.io.platform.irq.machineSoftware.poke(false.B)
      dut.io.platform.irq.machineTimer.poke(false.B)
      dut.io.platform.irq.machineExternal.poke(false.B)
      dut.io.debug.retire.ready.poke(traceReady.B)
    }

    private def advance(): Unit = {
      val consumeI = iResp.isDefined && dut.io.mem.instruction.r.ready.peek().litToBoolean
      val consumeD = dResp.isDefined && dut.io.mem.data.r.ready.peek().litToBoolean
      val consumeB = bResp.isDefined && dut.io.mem.data.b.ready.peek().litToBoolean
      val newI =
        if (dut.io.mem.instruction.ar.valid.peek().litToBoolean && dut.io.mem.instruction.ar.ready.peek().litToBoolean) {
          val id = dut.io.mem.instruction.ar.bits.id.peek().litValue
          Some((mem.readWord(dut.io.mem.instruction.ar.bits.addr.peek().litValue.toLong & 0xffffffffL), 0, id))
        } else None
      val newD =
        if (dut.io.mem.data.ar.valid.peek().litToBoolean && dut.io.mem.data.ar.ready.peek().litToBoolean) {
          val addr = dut.io.mem.data.ar.bits.addr.peek().litValue.toLong & 0xffffffffL
          val lock = dut.io.mem.data.ar.bits.lock.peek().litToBoolean
          val id = dut.io.mem.data.ar.bits.id.peek().litValue
          Some((mem.read(addr, lock), 0, id))
        } else None
      val nextAw =
        if (dut.io.mem.data.aw.valid.peek().litToBoolean && dut.io.mem.data.aw.ready.peek().litToBoolean) {
          Some(
            dut.io.mem.data.aw.bits.addr.peek().litValue.toLong & 0xffffffffL,
            dut.io.mem.data.aw.bits.lock.peek().litToBoolean,
            dut.io.mem.data.aw.bits.id.peek().litValue)
        } else aw
      val nextW =
        if (dut.io.mem.data.w.valid.peek().litToBoolean && dut.io.mem.data.w.ready.peek().litToBoolean) {
          Some(dut.io.mem.data.w.bits.data.peek().litValue, dut.io.mem.data.w.bits.strb.peek().litValue.toInt)
        } else w
      val newB = (nextAw, nextW) match {
        case (Some((addr, lock, id)), Some((data, strb))) =>
          aw = None
          w = None
          Some((mem.write(addr, data, strb, lock), id))
        case _ =>
          aw = nextAw
          w = nextW
          None
      }

      dut.clock.step()
      if (consumeI) iResp = None
      if (consumeD) dResp = None
      if (consumeB) bResp = None
      newI.foreach(v => iResp = Some(v))
      newD.foreach(v => dResp = Some(v))
      newB.foreach(v => bResp = Some(v))
    }
  }

  private final class ZeroWaitMemory {
    private val bytes = mutable.Map.empty[Long, Int].withDefaultValue(0)
    private var reservation: Option[Long] = None

    def loadWords(base: Long, words: Seq[BigInt]): Unit =
      words.zipWithIndex.foreach { case (word, index) => writeRawWord(base + index.toLong * 4L, word) }

    def loadHalfWords(base: Long, halfWords: Seq[Int]): Unit =
      halfWords.zipWithIndex.foreach { case (half, index) =>
        val addr = base + index.toLong * 2L
        bytes(addr & 0xffffffffL) = half & 0xff
        bytes((addr + 1) & 0xffffffffL) = (half >>> 8) & 0xff
      }

    def readWord(addr: Long): BigInt = readAlignedWord(addr & ~3L)

    def read(addr: Long, lock: Boolean): BigInt = {
      val base = addr & ~3L
      if (lock) reservation = Some(base)
      readAlignedWord(base)
    }

    def write(addr: Long, data: BigInt, wstrb: Int, lock: Boolean): Int = {
      val base = addr & ~3L
      if (lock) {
        val success = reservation.contains(base)
        reservation = None
        if (success) writeWithStrobes(base, data, wstrb)
        if (success) 1 else 0
      } else {
        writeWithStrobes(base, data, wstrb)
        if (reservation.contains(base)) reservation = None
        0
      }
    }

    private def readAlignedWord(base: Long): BigInt =
      (0 until 4).foldLeft(BigInt(0)) { case (acc, i) => acc | (BigInt(bytes((base + i) & 0xffffffffL)) << (8 * i)) }

    private def writeRawWord(base: Long, value: BigInt): Unit =
      writeWithStrobes(base, value, 0xf)

    private def writeWithStrobes(base: Long, value: BigInt, wstrb: Int): Unit =
      (0 until 4).foreach { i =>
        if (((wstrb >>> i) & 1) == 1) {
          bytes((base + i) & 0xffffffffL) = ((value >> (8 * i)) & 0xff).toInt
        }
      }
  }

  private def peekTrace(dut: NovaV1): TraceRecord = {
    val t = dut.io.debug.retire.bits
    TraceRecord(
      retired = t.retired.peek().litToBoolean,
      pc = t.pc.peek().litValue,
      instr = t.instr.peek().litValue,
      instrLen = t.instrLen.peek().litValue,
      nextPc = t.nextPc.peek().litValue,
      rd = t.rd.peek().litValue,
      rdWen = t.rdWen.peek().litToBoolean,
      rdValue = t.rdValue.peek().litValue,
      trap = t.trap.peek().litToBoolean,
      trapCause = t.trapCause.peek().litValue,
      trapValue = t.trapValue.peek().litValue,
      memValid = t.memValid.peek().litToBoolean,
      memWrite = t.memWrite.peek().litToBoolean,
      memAddr = t.memAddr.peek().litValue,
      memSize = t.memSize.peek().litValue,
      memWdata = t.memWdata.peek().litValue,
      memRdata = t.memRdata.peek().litValue,
      memAtomic = t.memAtomic.peek().litValue,
      csrValid = t.csrValid.peek().litToBoolean,
      csrAddr = t.csrAddr.peek().litValue
    )
  }

  private def checkTrace(trace: TraceRecord, expected: Expected): Unit = {
    assert(trace.pc == BigInt(expected.pc), s"pc mismatch at expected 0x${expected.pc.toHexString}")
    assert(trace.instr == (expected.instr & U32), s"instruction mismatch at pc 0x${expected.pc.toHexString}")
    assert(trace.instrLen == BigInt(expected.len), s"instruction length mismatch at pc 0x${expected.pc.toHexString}")
    assert(trace.nextPc == BigInt(expected.nextPc), s"next pc mismatch at pc 0x${expected.pc.toHexString}")
    assert(trace.retired == expected.retired, s"retired flag mismatch at pc 0x${expected.pc.toHexString}")

    expected.trap match {
      case Some((cause, value)) =>
        assert(trace.trap, s"expected trap at pc 0x${expected.pc.toHexString}")
        assert(trace.trapCause == cause)
        assert(trace.trapValue == value)
      case None =>
        assert(!trace.trap, s"unexpected trap at pc 0x${expected.pc.toHexString}, cause=${trace.trapCause}")
    }

    expected.rd match {
      case Some((rd, value)) =>
        assert(trace.rdWen, s"expected rd write at pc 0x${expected.pc.toHexString}")
        assert(trace.rd == BigInt(rd))
        assert(
          trace.rdValue == (value & U32),
          s"rd value mismatch at pc 0x${expected.pc.toHexString}: got 0x${trace.rdValue.toString(16)}, expected 0x${(value & U32).toString(16)}")
      case None =>
        assert(!trace.rdWen, s"unexpected rd write at pc 0x${expected.pc.toHexString}")
    }

    expected.mem match {
      case Some(mem) =>
        assert(trace.memValid, s"expected memory access at pc 0x${expected.pc.toHexString}")
        assert(trace.memAddr == BigInt(mem.addr))
        assert(trace.memWrite == mem.write)
        assert(trace.memSize == BigInt(mem.size))
        assert(trace.memWdata == (mem.wdata & U32), s"memory write data mismatch at pc 0x${expected.pc.toHexString}")
        assert(
          trace.memRdata == (mem.rdata & U32),
          s"memory read data mismatch at pc 0x${expected.pc.toHexString}: got 0x${trace.memRdata.toString(16)}, expected 0x${(mem.rdata & U32).toString(16)}")
        assert(trace.memAtomic == BigInt(mem.atomic))
      case None =>
        assert(!trace.memValid, s"unexpected memory access at pc 0x${expected.pc.toHexString}")
    }

    expected.csr.foreach { addr =>
      assert(trace.csrValid)
      assert(trace.csrAddr == BigInt(addr))
    }
  }

  private def signed32(value: BigInt): Long = {
    val v = (value & U32).toLong
    if ((v & 0x80000000L) != 0) v - 0x100000000L else v
  }

  private def addi(rd: Int, rs1: Int, imm: Int): BigInt = encI(imm, rs1, 0, rd, 0x13)
  private def load(rd: Int, rs1: Int, imm: Int, funct3: Int): BigInt = encI(imm, rs1, funct3, rd, 0x03)
  private def jalr(rd: Int, rs1: Int, imm: Int): BigInt = encI(imm, rs1, 0, rd, 0x67)
  private def lui(rd: Int, imm20: Int): BigInt = (BigInt(imm20 & 0xfffff) << 12) | (BigInt(rd) << 7) | 0x37
  private def store(rs2: Int, rs1: Int, imm: Int, funct3: Int): BigInt =
    ((BigInt(imm) & 0xfe0) << 20) | (BigInt(rs2) << 20) | (BigInt(rs1) << 15) |
      (BigInt(funct3) << 12) | ((BigInt(imm) & 0x1f) << 7) | 0x23
  private def branch(rs1: Int, rs2: Int, funct3: Int, imm: Int): BigInt = {
    val u = BigInt(imm) & 0x1fff
    (((u >> 12) & 1) << 31) | (((u >> 5) & 0x3f) << 25) | (BigInt(rs2) << 20) |
      (BigInt(rs1) << 15) | (BigInt(funct3) << 12) | (((u >> 1) & 0xf) << 8) |
      (((u >> 11) & 1) << 7) | 0x63
  }
  private def op(rd: Int, rs1: Int, rs2: Int, funct3: Int, funct7: Int): BigInt =
    (BigInt(funct7) << 25) | (BigInt(rs2) << 20) | (BigInt(rs1) << 15) |
      (BigInt(funct3) << 12) | (BigInt(rd) << 7) | 0x33
  private def opImmShift(rd: Int, rs1: Int, shamt: Int, funct3: Int, funct7: Int): BigInt =
    (BigInt(funct7) << 25) | (BigInt(shamt & 0x1f) << 20) | (BigInt(rs1) << 15) |
      (BigInt(funct3) << 12) | (BigInt(rd) << 7) | 0x13
  private def csr(rd: Int, rs1: Int, addr: Int, funct3: Int): BigInt =
    (BigInt(addr & 0xfff) << 20) | (BigInt(rs1) << 15) | (BigInt(funct3) << 12) |
      (BigInt(rd) << 7) | 0x73
  private def amo(rd: Int, rs1: Int, rs2: Int, funct5: Int): BigInt =
    (BigInt(funct5) << 27) | (BigInt(rs2) << 20) | (BigInt(rs1) << 15) |
      (BigInt(2) << 12) | (BigInt(rd) << 7) | 0x2f
  private def encI(imm: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): BigInt =
    ((BigInt(imm) & 0xfff) << 20) | (BigInt(rs1) << 15) | (BigInt(funct3) << 12) |
      (BigInt(rd) << 7) | opcode
  private def fenceI: BigInt = BigInt("0000100f", 16)
}
