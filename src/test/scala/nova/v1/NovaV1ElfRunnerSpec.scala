package nova.v1

import chisel3._
import chisel3.simulator.PeekPokeAPI
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.funsuite.AnyFunSuite

import java.io.PrintWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable
import scala.util.Try

final class NovaV1ElfRunnerSpec extends AnyFunSuite with ChiselSim {
  test("configured ELF runs through the AXI-backed NovaV1 architectural runner") {
    sys.env.get("NOVA_ELF") match {
      case None => succeed
      case Some(elfPath) =>
        val config = NovaV1ElfRunner.Config.fromEnv(elfPath)
        simulate(new NovaV1(NovaV1Params(bootPc = 0, hartId = 0, enableTrace = true, startHalted = true))) { dut =>
          val result = NovaV1ElfRunner.run(dut, config)
          assert(result.passed, result.message)
        }
    }
  }
}

object NovaV1ElfRunner extends PeekPokeAPI {
  private val U32 = BigInt("ffffffff", 16)
  private val ClintMtimeCmp = 0x02004000L
  private val ClintMtime = 0x0200bff8L

  final case class Config(
      elfPath: Path,
      tracePath: Path,
      summaryPath: Path,
      maxCycles: Long,
      expectTohost: Long,
      expectSignature: Option[Long],
      failOnUnexpectedSyncTrap: Boolean,
      requireTimerInterrupt: Boolean,
      requireFreeRtosProgress: Boolean)

  object Config {
    def fromEnv(elfPath: String): Config = {
      val outDir = Paths.get(sys.env.getOrElse("NOVA_OUT_DIR", "out/nova-run"))
      val trace = Paths.get(sys.env.getOrElse("NOVA_TRACE", outDir.resolve("trace.jsonl").toString))
      val summary = Paths.get(sys.env.getOrElse("NOVA_RUN_SUMMARY", outDir.resolve("summary.json").toString))
      Config(
        elfPath = Paths.get(elfPath),
        tracePath = trace,
        summaryPath = summary,
        maxCycles = sys.env.get("NOVA_MAX_CYCLES").flatMap(s => Try(java.lang.Long.decode(s).toLong).toOption).getOrElse(200000L),
        expectTohost = sys.env.get("NOVA_EXPECT_TOHOST").flatMap(s => Try(java.lang.Long.decode(s).toLong).toOption).getOrElse(1L),
        expectSignature = sys.env.get("NOVA_EXPECT_SIGNATURE").flatMap(s => Try(java.lang.Long.decode(s).toLong).toOption),
        failOnUnexpectedSyncTrap = sys.env.get("NOVA_FAIL_ON_SYNC_TRAP").forall(_ != "0"),
        requireTimerInterrupt = sys.env.get("NOVA_REQUIRE_TIMER_INTERRUPT").contains("1"),
        requireFreeRtosProgress = sys.env.get("NOVA_REQUIRE_FREERTOS_PROGRESS").contains("1")
      )
    }
  }

  final case class Result(passed: Boolean, message: String)

  final case class TraceRecord(
      cycle: Long,
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
      memFault: Boolean,
      memAtomic: BigInt,
      csrValid: Boolean,
      csrAddr: BigInt,
      csrWrite: Boolean,
      csrWdata: BigInt,
      csrRdata: BigInt,
      xregs: Vector[BigInt],
      tohost: BigInt,
      signature: Option[BigInt])

  def run(dut: NovaV1, config: Config): Result = {
    val elf = ElfImage.load(config.elfPath)
    val mem = new RunnerMemory(elf)
    Files.createDirectories(config.tracePath.getParent)
    Files.createDirectories(config.summaryPath.getParent)

    var xregs = Vector.fill(32)(BigInt(0))
    var cycles = 0L
    var retired = 0L
    var traps = 0L
    var syncTrap: Option[TraceRecord] = None
    var timerInterruptSeen = false
    var stopped = false
    var stopReason = "timeout"

    val traceOut = new PrintWriter(Files.newBufferedWriter(config.tracePath))
    try {
      val axi = new RunnerAxi(dut, mem)
      axi.controlWrite(NovaV1ControlMap.BOOT_PC, elf.start)
      axi.controlWrite(NovaV1ControlMap.COMMAND, 0x0c)

      while (!stopped && cycles < config.maxCycles) {
        axi.driveDefaults()
        if (dut.io.debug.retire.valid.peek().litToBoolean) {
          val beforeUpdate = peekTrace(dut, cycles, xregs, mem)
          val afterRegs =
            if (beforeUpdate.retired && beforeUpdate.rdWen && beforeUpdate.rd != 0) {
              xregs.updated(beforeUpdate.rd.toInt, beforeUpdate.rdValue & U32)
            } else {
              xregs
            }
          xregs = afterRegs.updated(0, BigInt(0))
          val record = beforeUpdate.copy(xregs = xregs)
          traceOut.println(recordJson(record))
          if (record.retired) retired += 1
          if (record.trap) {
            traps += 1
            if ((record.trapCause & BigInt("80000000", 16)) != 0) {
              if (record.trapCause == BigInt("80000007", 16)) timerInterruptSeen = true
            } else if (record.trapCause != 11 && syncTrap.isEmpty) {
              syncTrap = Some(record)
            }
          }
          val tohost = mem.tohostValue
          if (tohost != 0) {
            stopped = true
            stopReason = if (tohost == config.expectTohost) "htif-pass" else s"htif-fail-$tohost"
          }
        }
        axi.advance()
        cycles += 1
        mem.tick()
      }
    } finally {
      traceOut.close()
    }

    val signature = mem.symbolWord("nova_pass_signature")
    val progress = mem.progressSnapshot
    val failures = mutable.ArrayBuffer.empty[String]
    if (!stopped) failures += s"timeout after ${config.maxCycles} cycles"
    if (mem.tohostValue != config.expectTohost) failures += f"tohost=0x${mem.tohostValue}%x expected 0x${config.expectTohost}%x"
    config.expectSignature.foreach { expected =>
      if (signature.getOrElse(BigInt(-1)) != BigInt(expected & 0xffffffffL)) {
        failures += f"nova_pass_signature=${signature.map(v => "0x" + v.toString(16)).getOrElse("missing")} expected 0x$expected%x"
      }
    }
    if (config.failOnUnexpectedSyncTrap) {
      syncTrap.foreach { t =>
        failures += s"unexpected synchronous trap at pc=0x${t.pc.toString(16)} cause=0x${t.trapCause.toString(16)} tval=0x${t.trapValue.toString(16)}"
      }
    }
    if (config.requireTimerInterrupt && !timerInterruptSeen) failures += "machine timer interrupt was not observed"
    if (config.requireFreeRtosProgress) {
      Seq(
        "nova_scheduler_started",
        "nova_tick_count",
        "nova_producer_count",
        "nova_consumer_count",
        "nova_queue_count",
        "nova_semaphore_count",
        "nova_critical_count"
      ).foreach { name =>
        if (progress.getOrElse(name, BigInt(0)) == 0) failures += s"$name did not advance"
      }
    }

    val summary =
      s"""|{
          |  "elf": "${json(config.elfPath.toString)}",
          |  "trace": "${json(config.tracePath.toString)}",
          |  "cycles": $cycles,
          |  "retired": $retired,
          |  "traps": $traps,
          |  "timerInterruptSeen": $timerInterruptSeen,
          |  "stopReason": "${json(stopReason)}",
          |  "tohost": "0x${java.lang.Long.toUnsignedString(mem.tohostValue, 16)}",
          |  "nova_pass_signature": ${signature.map(v => "\"" + "0x" + v.toString(16) + "\"").getOrElse("null")},
          |  "progress": {${progress.toSeq.sortBy(_._1).map { case (k, v) => "\"" + json(k) + "\": \"0x" + v.toString(16) + "\"" }.mkString(", ")}},
          |  "passed": ${failures.isEmpty},
          |  "failures": [${failures.map(f => "\"" + json(f) + "\"").mkString(", ")}]
          |}
          |""".stripMargin
    Files.writeString(config.summaryPath, summary)

    if (failures.isEmpty) Result(passed = true, s"PASS: ${config.elfPath} retired $retired instructions in $cycles cycles")
    else Result(passed = false, failures.mkString("; "))
  }

  private final class RunnerAxi(dut: NovaV1, mem: RunnerMemory) {
    private var iResp: Option[(BigInt, Int, BigInt)] = None
    private var dResp: Option[(BigInt, Int, BigInt)] = None
    private var bResp: Option[(Int, BigInt)] = None
    private var aw: Option[(Long, Boolean, BigInt)] = None
    private var w: Option[(BigInt, Int)] = None

    def controlWrite(addr: Int, data: BigInt): Unit = {
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
          assert(dut.io.reg.control.b.bits.resp.peek().litValue == BigInt(0))
          advance()
          return
        }
        advance()
      }
      throw new AssertionError("timed out waiting for AXI-Lite write response")
    }

    def driveDefaults(): Unit = {
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

      dut.io.platform.mtime.poke(mem.mtimeBig)
      dut.io.platform.irq.machineSoftware.poke(false.B)
      dut.io.platform.irq.machineTimer.poke(mem.machineTimerPending.B)
      dut.io.platform.irq.machineExternal.poke(false.B)
      dut.io.debug.retire.ready.poke(true.B)
    }

    def advance(): Unit = {
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

  private def peekTrace(dut: NovaV1, cycle: Long, xregs: Vector[BigInt], mem: RunnerMemory): TraceRecord =
    TraceRecord(
      cycle = cycle,
      retired = dut.io.debug.retire.bits.retired.peek().litToBoolean,
      pc = dut.io.debug.retire.bits.pc.peek().litValue,
      instr = dut.io.debug.retire.bits.instr.peek().litValue,
      instrLen = dut.io.debug.retire.bits.instrLen.peek().litValue,
      nextPc = dut.io.debug.retire.bits.nextPc.peek().litValue,
      rd = dut.io.debug.retire.bits.rd.peek().litValue,
      rdWen = dut.io.debug.retire.bits.rdWen.peek().litToBoolean,
      rdValue = dut.io.debug.retire.bits.rdValue.peek().litValue,
      trap = dut.io.debug.retire.bits.trap.peek().litToBoolean,
      trapCause = dut.io.debug.retire.bits.trapCause.peek().litValue,
      trapValue = dut.io.debug.retire.bits.trapValue.peek().litValue,
      memValid = dut.io.debug.retire.bits.memValid.peek().litToBoolean,
      memWrite = dut.io.debug.retire.bits.memWrite.peek().litToBoolean,
      memAddr = dut.io.debug.retire.bits.memAddr.peek().litValue,
      memSize = dut.io.debug.retire.bits.memSize.peek().litValue,
      memWdata = dut.io.debug.retire.bits.memWdata.peek().litValue,
      memRdata = dut.io.debug.retire.bits.memRdata.peek().litValue,
      memFault = dut.io.debug.retire.bits.memFault.peek().litToBoolean,
      memAtomic = dut.io.debug.retire.bits.memAtomic.peek().litValue,
      csrValid = dut.io.debug.retire.bits.csrValid.peek().litToBoolean,
      csrAddr = dut.io.debug.retire.bits.csrAddr.peek().litValue,
      csrWrite = dut.io.debug.retire.bits.csrWrite.peek().litToBoolean,
      csrWdata = dut.io.debug.retire.bits.csrWdata.peek().litValue,
      csrRdata = dut.io.debug.retire.bits.csrRdata.peek().litValue,
      xregs = xregs,
      tohost = BigInt(mem.tohostValue),
      signature = mem.symbolWord("nova_pass_signature")
    )

  private def recordJson(t: TraceRecord): String = {
    val x = t.xregs.map(v => "\"" + "0x" + (v & U32).toString(16) + "\"").mkString("[", ",", "]")
    s"""{"cycle":${t.cycle},"retired":${t.retired},"pc":"0x${t.pc.toString(16)}","instr":"0x${t.instr.toString(16)}","instrLen":${t.instrLen},"nextPc":"0x${t.nextPc.toString(16)}","rd":${t.rd},"rdWen":${t.rdWen},"rdValue":"0x${(t.rdValue & U32).toString(16)}","trap":${t.trap},"trapCause":"0x${t.trapCause.toString(16)}","trapValue":"0x${t.trapValue.toString(16)}","memValid":${t.memValid},"memWrite":${t.memWrite},"memAddr":"0x${t.memAddr.toString(16)}","memSize":${t.memSize},"memWdata":"0x${(t.memWdata & U32).toString(16)}","memRdata":"0x${(t.memRdata & U32).toString(16)}","memFault":${t.memFault},"memAtomic":${t.memAtomic},"csrValid":${t.csrValid},"csrAddr":"0x${t.csrAddr.toString(16)}","csrWrite":${t.csrWrite},"csrWdata":"0x${(t.csrWdata & U32).toString(16)}","csrRdata":"0x${(t.csrRdata & U32).toString(16)}","xregs":$x,"tohost":"0x${t.tohost.toString(16)}","signature":${t.signature.map(v => "\"" + "0x" + v.toString(16) + "\"").getOrElse("null")}}"""
  }

  private def json(s: String): String = s.flatMap {
    case '\\' => "\\\\"
    case '"' => "\\\""
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case c => c.toString
  }

  private final class RunnerMemory(elf: ElfImage) {
    private val bytes = mutable.Map.empty[Long, Int].withDefaultValue(0)
    private var reservation: Option[Long] = None
    private var mtime: BigInt = BigInt(0)
    private var mtimecmp: BigInt = BigInt("ffffffffffffffff", 16)

    elf.segments.foreach { seg =>
      seg.bytes.zipWithIndex.foreach { case (b, i) => bytes(seg.addr + i.toLong) = b & 0xff }
      (seg.bytes.length until seg.memSize).foreach { i => bytes(seg.addr + i.toLong) = 0 }
    }

    def mtimeBig: BigInt = mtime & BigInt("ffffffffffffffff", 16)
    def machineTimerPending: Boolean = mtimeBig >= mtimecmp
    def tick(): Unit = mtime = (mtime + 1) & BigInt("ffffffffffffffff", 16)
    def tohostValue: Long = elf.symbols.get("tohost").map(addr => readDoubleWord(addr).toLong).getOrElse(0L)
    def symbolWord(name: String): Option[BigInt] = elf.symbols.get(name).map(addr => readAlignedWord(addr) & U32)
    def progressSnapshot: Map[String, BigInt] =
      Seq(
        "nova_scheduler_started",
        "nova_tick_count",
        "nova_producer_count",
        "nova_consumer_count",
        "nova_queue_count",
        "nova_semaphore_count",
        "nova_critical_count",
        "nova_stack_watermark"
      ).flatMap(name => symbolWord(name).map(value => name -> value)).toMap

    def readWord(addr: Long): BigInt = readAlignedWord(addr & ~3L)

    def read(addr: Long, lock: Boolean): BigInt = {
      if (isClint(addr)) {
        readClintWord(addr)
      } else {
        val base = addr & ~3L
        if (lock) reservation = Some(base)
        readAlignedWord(base)
      }
    }

    def write(addr: Long, data: BigInt, wstrb: Int, lock: Boolean): Int = {
      if (isClint(addr)) {
        writeClintWord(addr, data, wstrb)
        0
      } else {
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
    }

    private def isClint(addr: Long): Boolean =
      (addr >= ClintMtimeCmp && addr < ClintMtimeCmp + 8) || (addr >= ClintMtime && addr < ClintMtime + 8)

    private def readClintWord(addr: Long): BigInt = {
      val source = if (addr >= ClintMtime) mtimeBig else mtimecmp
      if ((addr & 4L) == 0L) source & U32 else (source >> 32) & U32
    }

    private def writeClintWord(addr: Long, data: BigInt, wstrb: Int): Unit = {
      val current = if (addr >= ClintMtime) mtimeBig else mtimecmp
      val shift = if ((addr & 4L) == 0L) 0 else 32
      val word = (0 until 4).foldLeft((current >> shift) & U32) { case (acc, i) =>
        if (((wstrb >>> i) & 1) == 1) {
          val clearMask = ~(BigInt(0xff) << (8 * i)) & U32
          (acc & clearMask) | (((data >> (8 * i)) & 0xff) << (8 * i))
        } else acc
      }
      val clear = ~(U32 << shift) & BigInt("ffffffffffffffff", 16)
      val updated = (current & clear) | ((word & U32) << shift)
      if (addr >= ClintMtime) mtime = updated else mtimecmp = updated
    }

    private def readByte(addr: Long): Int = bytes(addr & 0xffffffffL)
    private def readAlignedWord(base: Long): BigInt =
      (0 until 4).foldLeft(BigInt(0)) { case (acc, i) => acc | (BigInt(readByte(base + i)) << (8 * i)) }
    private def readDoubleWord(base: Long): BigInt =
      (0 until 8).foldLeft(BigInt(0)) { case (acc, i) => acc | (BigInt(readByte(base + i)) << (8 * i)) }
    private def writeRawWord(base: Long, value: BigInt): Unit = writeWithStrobes(base, value, 0xf)
    private def writeWithStrobes(base: Long, value: BigInt, wstrb: Int): Unit =
      (0 until 4).foreach { i =>
        if (((wstrb >>> i) & 1) == 1) bytes((base + i) & 0xffffffffL) = ((value >> (8 * i)) & 0xff).toInt
      }
  }

  private final case class Segment(addr: Long, bytes: Array[Byte], memSize: Int)
  private final case class ElfImage(entry: Long, start: Long, segments: Seq[Segment], symbols: Map[String, Long])

  private object ElfImage {
    def load(path: Path): ElfImage = {
      val data = Files.readAllBytes(path)
      val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
      require(data.length >= 52, s"$path is too small to be an ELF32 file")
      require(data(0) == 0x7f.toByte && data(1) == 'E'.toByte && data(2) == 'L'.toByte && data(3) == 'F'.toByte, s"$path is not an ELF file")
      require(data(4) == 1.toByte && data(5) == 1.toByte, s"$path is not an ELF32 little-endian file")
      val entry = u32(bb, 24)
      val phoff = u32(bb, 28).toInt
      val shoff = u32(bb, 32).toInt
      val phentsize = u16(bb, 42)
      val phnum = u16(bb, 44)
      val shentsize = u16(bb, 46)
      val shnum = u16(bb, 48)
      val segments = (0 until phnum).flatMap { i =>
        val off = phoff + i * phentsize
        val pType = u32(bb, off)
        if (pType == 1) {
          val pOffset = u32(bb, off + 4).toInt
          val pVaddr = u32(bb, off + 8)
          val pPaddr = u32(bb, off + 12)
          val pFilesz = u32(bb, off + 16).toInt
          val pMemsz = u32(bb, off + 20).toInt
          Some(Segment(if (pPaddr != 0) pPaddr else pVaddr, data.slice(pOffset, pOffset + pFilesz), pMemsz))
        } else None
      }
      val symbols = readSymbols(data, bb, shoff, shentsize, shnum)
      ElfImage(entry, symbols.getOrElse("_start", entry), segments, symbols)
    }

    private def readSymbols(data: Array[Byte], bb: ByteBuffer, shoff: Int, shentsize: Int, shnum: Int): Map[String, Long] = {
      if (shoff == 0 || shnum == 0) return Map.empty
      val sections = (0 until shnum).map { i =>
        val off = shoff + i * shentsize
        Section(
          name = u32(bb, off).toInt,
          tpe = u32(bb, off + 4),
          offset = u32(bb, off + 16).toInt,
          size = u32(bb, off + 20).toInt,
          link = u32(bb, off + 24).toInt,
          entsize = u32(bb, off + 36).toInt
        )
      }
      val out = mutable.Map.empty[String, Long]
      sections.zipWithIndex.foreach { case (sec, _) =>
        if (sec.tpe == 2 && sec.entsize >= 16 && sec.link >= 0 && sec.link < sections.size) {
          val str = sections(sec.link)
          var cursor = sec.offset
          while (cursor + sec.entsize <= sec.offset + sec.size) {
            val nameOff = u32(bb, cursor).toInt
            val value = u32(bb, cursor + 4)
            val name = cString(data, str.offset + nameOff, str.offset + str.size)
            if (name.nonEmpty) out(name) = value
            cursor += sec.entsize
          }
        }
      }
      out.toMap
    }

    private final case class Section(name: Int, tpe: Long, offset: Int, size: Int, link: Int, entsize: Int)
    private def u16(bb: ByteBuffer, off: Int): Int = bb.getShort(off) & 0xffff
    private def u32(bb: ByteBuffer, off: Int): Long = bb.getInt(off).toLong & 0xffffffffL
    private def cString(data: Array[Byte], start: Int, limit: Int): String = {
      if (start < 0 || start >= data.length || start >= limit) ""
      else {
        var end = start
        while (end < data.length && end < limit && data(end) != 0) end += 1
        new String(data.slice(start, end), "UTF-8")
      }
    }
  }

  private def signed32(value: BigInt): Long = {
    val v = (value & U32).toLong
    if ((v & 0x80000000L) != 0) v - 0x100000000L else v
  }
}
