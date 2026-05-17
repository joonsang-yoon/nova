package nova.v1

import chisel3._
import chisel3.util._

final case class NovaV1Params(
  bootPc:           BigInt = 0,
  hartId:           Int = 0,
  enableTrace:      Boolean = true,
  startHalted:      Boolean = true,
  axiIdWidth:       Int = 4,
  instructionAxiId: BigInt = 0,
  dataAxiId:        BigInt = 1
) {
  require(axiIdWidth > 0 && axiIdWidth <= 16, "NovaV1 AXI ID width must be between 1 and 16 bits")
  private val idLimit = BigInt(1) << axiIdWidth
  require(instructionAxiId >= 0 && instructionAxiId < idLimit, s"instructionAxiId must fit in $axiIdWidth bits")
  require(dataAxiId >= 0 && dataAxiId < idLimit, s"dataAxiId must fit in $axiIdWidth bits")
}

object NovaConstants {
  val XLEN = 32
  val AxiAddressWidth = 32
  val AxiDataWidth = 32
  val MisaValue = "h40001107".U(32.W)
  val ResetCause = 0.U(32.W)
  val ArchitectureId = "h4e4f5641".U(32.W) // "NOVA"
  val Implementation = 1.U(32.W)
}

object NovaV1ControlMap {
  val IP_ID = 0x000
  val IP_VERSION = 0x004
  val INTERFACE_VERSION = 0x008
  val CAPABILITIES = 0x00c
  val HART_ID = 0x010
  val MISA = 0x014
  val CORE_STATUS = 0x020
  val TRACE_STATUS = 0x024
  val CURRENT_PC = 0x028
  val LAST_TRAP_CAUSE = 0x02c
  val LAST_TRAP_VALUE = 0x030
  val COMMAND = 0x040
  val BOOT_PC = 0x044

  val InterfaceVersionValue = 1

  val CMD_SOFT_RESET = 0
  val CMD_HALT = 1
  val CMD_RESUME = 2
  val CMD_SET_PC = 3
  val CMD_CLEAR_TRAP = 4
}

object NovaMemSize {
  val byte = 0.U(2.W)
  val half = 1.U(2.W)
  val word = 2.U(2.W)
}

object NovaAxi4Size {
  val byte = 0.U(3.W)
  val half = 1.U(3.W)
  val word = 2.U(3.W)
}

object NovaAxi4Burst {
  val fixed = 0.U(2.W)
  val incr = 1.U(2.W)
  val wrap = 2.U(2.W)
}

object NovaAxi4Prot {
  val data = 0.U(3.W)
  val instruction = 4.U(3.W)
}

object NovaAxi4Resp {
  val okay = 0.U(2.W)
  val exOkay = 1.U(2.W)
  val slvErr = 2.U(2.W)
  val decErr = 3.U(2.W)

  def isError(resp: UInt): Bool = resp(1)
}

object NovaAtomicOp {
  val none = 0.U(4.W)
  val lr = 1.U(4.W)
  val sc = 2.U(4.W)
  val swap = 3.U(4.W)
  val add = 4.U(4.W)
  val xor = 5.U(4.W)
  val and = 6.U(4.W)
  val or = 7.U(4.W)
  val min = 8.U(4.W)
  val max = 9.U(4.W)
  val minu = 10.U(4.W)
  val maxu = 11.U(4.W)
}

object NovaTrapCause {
  val instrAddrMisaligned = 0.U(32.W)
  val instrAccessFault = 1.U(32.W)
  val illegalInstruction = 2.U(32.W)
  val breakpoint = 3.U(32.W)
  val loadAddrMisaligned = 4.U(32.W)
  val loadAccessFault = 5.U(32.W)
  val storeAddrMisaligned = 6.U(32.W)
  val storeAccessFault = 7.U(32.W)
  val ecallM = 11.U(32.W)
  val machineTimerIrq = "h80000007".U(32.W)
  val machineExternalIrq = "h8000000b".U(32.W)
  val machineSoftwareIrq = "h80000003".U(32.W)
}

final class NovaAxi4Address(idWidth: Int) extends Bundle {
  val id = UInt(idWidth.W)
  val addr = UInt(32.W)
  val len = UInt(8.W)
  val size = UInt(3.W)
  val burst = UInt(2.W)
  val lock = Bool()
  val cache = UInt(4.W)
  val prot = UInt(3.W)
  val qos = UInt(4.W)
  val region = UInt(4.W)
}

final class NovaAxi4WriteData extends Bundle {
  val data = UInt(32.W)
  val strb = UInt(4.W)
  val last = Bool()
}

final class NovaAxi4ReadData(idWidth: Int) extends Bundle {
  val id = UInt(idWidth.W)
  val data = UInt(32.W)
  val resp = UInt(2.W)
  val last = Bool()
}

final class NovaAxi4WriteResponse(idWidth: Int) extends Bundle {
  val id = UInt(idWidth.W)
  val resp = UInt(2.W)
}

final class NovaAxi4ReadMasterIO(idWidth: Int) extends Bundle {
  val ar = Decoupled(new NovaAxi4Address(idWidth))
  val r = Flipped(Decoupled(new NovaAxi4ReadData(idWidth)))
}

final class NovaAxi4MasterIO(idWidth: Int) extends Bundle {
  val ar = Decoupled(new NovaAxi4Address(idWidth))
  val r = Flipped(Decoupled(new NovaAxi4ReadData(idWidth)))
  val aw = Decoupled(new NovaAxi4Address(idWidth))
  val w = Decoupled(new NovaAxi4WriteData)
  val b = Flipped(Decoupled(new NovaAxi4WriteResponse(idWidth)))
}

final class NovaAxi4LiteAddress extends Bundle {
  val addr = UInt(12.W)
  val prot = UInt(3.W)
}

final class NovaAxi4LiteWriteData extends Bundle {
  val data = UInt(32.W)
  val strb = UInt(4.W)
}

final class NovaAxi4LiteReadData extends Bundle {
  val data = UInt(32.W)
  val resp = UInt(2.W)
}

final class NovaAxi4LiteWriteResponse extends Bundle {
  val resp = UInt(2.W)
}

final class NovaAxi4LiteSlaveIO extends Bundle {
  val aw = Flipped(Decoupled(new NovaAxi4LiteAddress))
  val w = Flipped(Decoupled(new NovaAxi4LiteWriteData))
  val b = Decoupled(new NovaAxi4LiteWriteResponse)
  val ar = Flipped(Decoupled(new NovaAxi4LiteAddress))
  val r = Decoupled(new NovaAxi4LiteReadData)
}

final class NovaV1MemoryPorts(idWidth: Int) extends Bundle {
  val instruction = new NovaAxi4ReadMasterIO(idWidth)
  val data = new NovaAxi4MasterIO(idWidth)
}

final class NovaV1RegisterPorts extends Bundle {
  val control = new NovaAxi4LiteSlaveIO
}

final class NovaV1MachineInterruptInputs extends Bundle {
  val machineSoftware = Bool()
  val machineTimer = Bool()
  val machineExternal = Bool()
}

final class NovaV1PlatformInputs extends Bundle {
  val mtime = UInt(64.W)
  val irq = new NovaV1MachineInterruptInputs
}

final class NovaV1HartDebugStatus extends Bundle {
  val running = Bool()
  val halted = Bool()
  val trapSeen = Bool()
  val interruptPending = Bool()
  val traceBackpressured = Bool()
  val currentPc = UInt(32.W)
}

final class NovaV1DebugPorts extends Bundle {
  val hart = Output(new NovaV1HartDebugStatus)
  val retire = Decoupled(new NovaV1RetireTrace)
}

final class NovaV1RetireTrace extends Bundle {
  val retired = Bool()
  val pc = UInt(32.W)
  val instr = UInt(32.W)
  val instrLen = UInt(3.W)
  val nextPc = UInt(32.W)

  val rd = UInt(5.W)
  val rdWen = Bool()
  val rdValue = UInt(32.W)

  val trap = Bool()
  val trapCause = UInt(32.W)
  val trapValue = UInt(32.W)

  val memValid = Bool()
  val memWrite = Bool()
  val memAddr = UInt(32.W)
  val memSize = UInt(2.W)
  val memWdata = UInt(32.W)
  val memRdata = UInt(32.W)
  val memFault = Bool()
  val memAtomic = UInt(4.W)

  val csrValid = Bool()
  val csrAddr = UInt(12.W)
  val csrWrite = Bool()
  val csrWdata = UInt(32.W)
  val csrRdata = UInt(32.W)
}
