package nova.v1

import chisel3._
import chisel3.util._

final class NovaV1(params: NovaV1Params) extends Module {
  import NovaAtomicOp._
  import NovaAxi4Burst.{incr => axiIncr}
  import NovaAxi4Prot.{data => axiDataProt, instruction => axiInstructionProt}
  import NovaAxi4Size.{word => axiWord}
  import NovaAxi4Resp._
  import NovaConstants._
  import NovaV1ControlMap._
  import NovaISA._
  import NovaMemSize._
  import NovaTrapCause._

  require(AxiAddressWidth == 32, "NovaV1 AXI address width is fixed at 32 bits")
  require(AxiDataWidth == 32, "NovaV1 AXI data width is fixed at 32 bits")

  val io = IO(new NovaV1IO(params))

  private val MSTATUS_MIE = 3
  private val MSTATUS_MPIE = 7
  private val MSTATUS_MPP = 11

  private val CSR_CYCLE = "hc00".U(12.W)
  private val CSR_TIME = "hc01".U(12.W)
  private val CSR_INSTRET = "hc02".U(12.W)
  private val CSR_CYCLEH = "hc80".U(12.W)
  private val CSR_TIMEH = "hc81".U(12.W)
  private val CSR_INSTRETH = "hc82".U(12.W)
  private val CSR_MSTATUS = "h300".U(12.W)
  private val CSR_MISA = "h301".U(12.W)
  private val CSR_MIE = "h304".U(12.W)
  private val CSR_MTVEC = "h305".U(12.W)
  private val CSR_MCOUNTEREN = "h306".U(12.W)
  private val CSR_MSTATUSH = "h310".U(12.W)
  private val CSR_MCOUNTINHIBIT = "h320".U(12.W)
  private val CSR_MSCRATCH = "h340".U(12.W)
  private val CSR_MEPC = "h341".U(12.W)
  private val CSR_MCAUSE = "h342".U(12.W)
  private val CSR_MTVAL = "h343".U(12.W)
  private val CSR_MIP = "h344".U(12.W)
  private val CSR_MCYCLE = "hb00".U(12.W)
  private val CSR_MINSTRET = "hb02".U(12.W)
  private val CSR_MCYCLEH = "hb80".U(12.W)
  private val CSR_MINSTRETH = "hb82".U(12.W)
  private val CSR_MVENDORID = "hf11".U(12.W)
  private val CSR_MARCHID = "hf12".U(12.W)
  private val CSR_MIMPID = "hf13".U(12.W)
  private val CSR_MHARTID = "hf14".U(12.W)

  val (
    sHalted :: sFetch0Req :: sFetch0Resp :: sFetch1Req :: sFetch1Resp :: sExec ::
    sLoadReq :: sLoadResp :: sStoreReq :: sStoreResp ::
    sLrReq :: sLrResp :: sScReq :: sScResp ::
    sAmoReadReq :: sAmoReadResp :: sAmoWriteReq :: sAmoWriteResp :: Nil
  ) = Enum(18)

  val pc = RegInit(params.bootPc.U(32.W))
  val state = RegInit(if (params.startHalted) sHalted else sFetch0Req)
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val bootPc = RegInit(params.bootPc.U(32.W))
  val trapSeen = RegInit(false.B)
  val haltRequested = RegInit(false.B)

  val mstatus = RegInit(0.U(32.W))
  val mstatush = RegInit(0.U(32.W))
  val mie = RegInit(0.U(32.W))
  val mipSoftware = RegInit(0.U(32.W))
  val mtvec = RegInit(0.U(32.W))
  val mscratch = RegInit(0.U(32.W))
  val mepc = RegInit(0.U(32.W))
  val mcause = RegInit(ResetCause)
  val mtval = RegInit(0.U(32.W))
  val mcounteren = RegInit(0.U(32.W))
  val mcountinhibit = RegInit(0.U(32.W))
  val mcycle = RegInit(0.U(64.W))
  val minstret = RegInit(0.U(64.W))

  val fetchWord0 = RegInit(0.U(32.W))
  val rawFetchReg = RegInit(0.U(32.W))
  val fetchFaultReg = RegInit(false.B)

  val lsuAddr = RegInit(0.U(32.W))
  val lsuSize = RegInit(word)
  val lsuWrite = RegInit(false.B)
  val lsuRd = RegInit(0.U(5.W))
  val lsuFunct3 = RegInit(0.U(3.W))
  val lsuWdata = RegInit(0.U(32.W))
  val lsuWstrb = RegInit(0.U(4.W))
  val lsuAtomic = RegInit(none)
  val lsuAq = RegInit(false.B)
  val lsuRl = RegInit(false.B)
  val lsuAwPending = RegInit(false.B)
  val lsuWPending = RegInit(false.B)
  val amoOld = RegInit(0.U(32.W))
  val amoWriteData = RegInit(0.U(32.W))

  val controlAwValid = RegInit(false.B)
  val controlAwAddr = RegInit(0.U(12.W))
  val controlWValid = RegInit(false.B)
  val controlWData = RegInit(0.U(32.W))
  val controlWStrb = RegInit(0.U(4.W))
  val controlBValid = RegInit(false.B)
  val controlBResp = RegInit(okay)
  val controlRValid = RegInit(false.B)
  val controlRData = RegInit(0.U(32.W))
  val controlRResp = RegInit(okay)

  val commandSoftReset = WireDefault(false.B)
  val commandHalt = WireDefault(false.B)
  val commandResume = WireDefault(false.B)
  val commandSetPc = WireDefault(false.B)
  val commandClearTrap = WireDefault(false.B)
  val traceCanAccept = WireDefault(true.B)
  val traceBlocked = WireDefault(false.B)

  def applyStrobes(old: UInt, data: UInt, strb: UInt): UInt = {
    val bytes = (0 until 4).map { i =>
      Mux(strb(i), data(8 * i + 7, 8 * i), old(8 * i + 7, 8 * i))
    }
    Cat(bytes.reverse)
  }

  val mipEffective = mipSoftware |
    Mux(io.platform.irq.machineSoftware, "h00000008".U(32.W), 0.U(32.W)) |
    Mux(io.platform.irq.machineTimer, "h00000080".U(32.W), 0.U(32.W)) |
    Mux(io.platform.irq.machineExternal, "h00000800".U(32.W), 0.U(32.W))

  val irqPendingBits = mipEffective & mie
  val globalMie = mstatus(MSTATUS_MIE)
  val pendingMachineSoftware = globalMie && irqPendingBits(3)
  val pendingMachineTimer = globalMie && irqPendingBits(7)
  val pendingMachineExternal = globalMie && irqPendingBits(11)
  val interruptPending = pendingMachineExternal || pendingMachineTimer || pendingMachineSoftware
  val interruptCause = Mux(
    pendingMachineExternal,
    machineExternalIrq,
    Mux(pendingMachineTimer, machineTimerIrq, machineSoftwareIrq)
  )

  io.debug.hart.running := state =/= sHalted
  io.debug.hart.halted := state === sHalted
  io.debug.hart.trapSeen := trapSeen
  io.debug.hart.interruptPending := interruptPending
  io.debug.hart.traceBackpressured := traceBlocked
  io.debug.hart.currentPc := pc

  def coreStatusWord: UInt =
    Cat(0.U(28.W), interruptPending, trapSeen, state === sHalted, state =/= sHalted)

  def traceStatusWord: UInt =
    Cat(0.U(30.W), traceBlocked, if (params.enableTrace) true.B else false.B)

  val controlCapabilities = (
    (if (params.enableTrace) 1 else 0) |
      (1 << 1) |
      (1 << 2) |
      (1 << 3) |
      (1 << 4) |
      (1 << 5) |
      (1 << 6) |
      (1 << 7) |
      (1 << 8) |
      (1 << 9)
  ).U(32.W)

  val controlWriteReady = controlAwValid && controlWValid && !controlBValid
  val controlReadData = WireDefault(0.U(32.W))
  val controlReadResp = WireDefault(okay)
  switch(io.reg.control.ar.bits.addr) {
    is(IP_ID.U) { controlReadData := ArchitectureId }
    is(IP_VERSION.U) { controlReadData := Implementation }
    is(INTERFACE_VERSION.U) { controlReadData := InterfaceVersionValue.U(32.W) }
    is(CAPABILITIES.U) { controlReadData := controlCapabilities }
    is(HART_ID.U) { controlReadData := params.hartId.U(32.W) }
    is(MISA.U) { controlReadData := MisaValue }
    is(CORE_STATUS.U) { controlReadData := coreStatusWord }
    is(TRACE_STATUS.U) { controlReadData := traceStatusWord }
    is(CURRENT_PC.U) { controlReadData := pc }
    is(LAST_TRAP_CAUSE.U) { controlReadData := mcause }
    is(LAST_TRAP_VALUE.U) { controlReadData := mtval }
    is(BOOT_PC.U) { controlReadData := bootPc }
  }
  when(
    io.reg.control.ar.bits.addr =/= IP_ID.U && io.reg.control.ar.bits.addr =/= IP_VERSION.U &&
      io.reg.control.ar.bits.addr =/= INTERFACE_VERSION.U && io.reg.control.ar.bits.addr =/= CAPABILITIES.U &&
      io.reg.control.ar.bits.addr =/= HART_ID.U && io.reg.control.ar.bits.addr =/= MISA.U &&
      io.reg.control.ar.bits.addr =/= CORE_STATUS.U && io.reg.control.ar.bits.addr =/= TRACE_STATUS.U &&
      io.reg.control.ar.bits.addr =/= CURRENT_PC.U && io.reg.control.ar.bits.addr =/= LAST_TRAP_CAUSE.U &&
      io.reg.control.ar.bits.addr =/= LAST_TRAP_VALUE.U && io.reg.control.ar.bits.addr =/= BOOT_PC.U
  ) {
    controlReadResp := slvErr
  }

  io.reg.control.aw.ready := !controlAwValid && !controlBValid
  io.reg.control.w.ready := !controlWValid && !controlBValid
  io.reg.control.b.valid := controlBValid
  io.reg.control.b.bits.resp := controlBResp
  io.reg.control.ar.ready := !controlRValid
  io.reg.control.r.valid := controlRValid
  io.reg.control.r.bits.data := controlRData
  io.reg.control.r.bits.resp := controlRResp

  when(io.reg.control.aw.fire) {
    controlAwValid := true.B
    controlAwAddr := io.reg.control.aw.bits.addr
  }
  when(io.reg.control.w.fire) {
    controlWValid := true.B
    controlWData := io.reg.control.w.bits.data
    controlWStrb := io.reg.control.w.bits.strb
  }
  when(controlWriteReady) {
    controlAwValid := false.B
    controlWValid := false.B
    controlBValid := true.B
    controlBResp := okay
    switch(controlAwAddr) {
      is(COMMAND.U) {
        commandSoftReset := controlWData(CMD_SOFT_RESET)
        commandHalt := controlWData(CMD_HALT)
        commandResume := controlWData(CMD_RESUME)
        commandSetPc := controlWData(CMD_SET_PC)
        commandClearTrap := controlWData(CMD_CLEAR_TRAP)
      }
      is(BOOT_PC.U) {
        bootPc := applyStrobes(bootPc, controlWData, controlWStrb)
      }
    }
    when(controlAwAddr =/= COMMAND.U && controlAwAddr =/= BOOT_PC.U) {
      controlBResp := slvErr
    }
  }
  when(io.reg.control.b.fire) {
    controlBValid := false.B
  }
  when(io.reg.control.ar.fire) {
    controlRValid := true.B
    controlRData := controlReadData
    controlRResp := controlReadResp
  }
  when(io.reg.control.r.fire) {
    controlRValid := false.B
  }

  def axiAddrDefaults(a: NovaAxi4Address, id: BigInt, prot: UInt): Unit = {
    a.id := id.U(params.axiIdWidth.W)
    a.addr := 0.U
    a.len := 0.U
    a.size := axiWord
    a.burst := axiIncr
    a.lock := false.B
    a.cache := 0.U
    a.prot := prot
    a.qos := 0.U
    a.region := 0.U
  }

  io.mem.instruction.ar.valid := (state === sFetch0Req && !interruptPending && !pc(
    0
  ) && !commandHalt) || state === sFetch1Req
  axiAddrDefaults(io.mem.instruction.ar.bits, params.instructionAxiId, axiInstructionProt)
  io.mem.instruction.ar.bits.addr := Mux(state === sFetch1Req, Cat(pc(31, 2), 0.U(2.W)) + 4.U, Cat(pc(31, 2), 0.U(2.W)))
  io.mem.instruction.r.ready := state === sFetch0Resp || state === sFetch1Resp

  io.mem.data.ar.valid := state === sLoadReq || state === sLrReq || state === sAmoReadReq
  axiAddrDefaults(io.mem.data.ar.bits, params.dataAxiId, axiDataProt)
  io.mem.data.ar.bits.addr := lsuAddr
  io.mem.data.ar.bits.size := Cat(0.U(1.W), lsuSize)
  io.mem.data.ar.bits.lock := state === sLrReq || state === sAmoReadReq
  io.mem.data.r.ready := (state === sLoadResp || state === sLrResp || state === sAmoReadResp) && traceCanAccept

  io.mem.data.aw.valid := (state === sStoreReq || state === sScReq || state === sAmoWriteReq) && lsuAwPending
  axiAddrDefaults(io.mem.data.aw.bits, params.dataAxiId, axiDataProt)
  io.mem.data.aw.bits.addr := lsuAddr
  io.mem.data.aw.bits.size := Cat(0.U(1.W), lsuSize)
  io.mem.data.aw.bits.lock := state === sScReq || state === sAmoWriteReq
  io.mem.data.w.valid := (state === sStoreReq || state === sScReq || state === sAmoWriteReq) && lsuWPending
  io.mem.data.w.bits.data := Mux(state === sAmoWriteReq, amoWriteData, lsuWdata)
  io.mem.data.w.bits.strb := lsuWstrb
  io.mem.data.w.bits.last := true.B
  io.mem.data.b.ready := (state === sStoreResp || state === sScResp || state === sAmoWriteResp) && traceCanAccept

  val rawFetch = rawFetchReg
  val isC = rawFetch(1, 0) =/= "b11".U
  val cExpanded = expandCompressed(rawFetch(15, 0))
  val instr = Mux(isC, cExpanded.instr, rawFetch)
  val instrLen = Mux(isC, 2.U(3.W), 4.U(3.W))
  val cQuadrant = rawFetch(1, 0)
  val cFunct3 = rawFetch(15, 13)
  val cRs1p = Cat("b01".U(2.W), rawFetch(9, 7))
  val cRs2p = Cat("b01".U(2.W), rawFetch(4, 2))
  val cRd = rawFetch(11, 7)
  val cRs2 = rawFetch(6, 2)
  val cRs1pVal = Mux(cRs1p === 0.U, 0.U, regs(cRs1p))
  val cRs2pVal = Mux(cRs2p === 0.U, 0.U, regs(cRs2p))
  val cRdVal = Mux(cRd === 0.U, 0.U, regs(cRd))
  val cRs2Val = Mux(cRs2 === 0.U, 0.U, regs(cRs2))
  val cJImm = signExtend(
    Cat(
      rawFetch(12),
      rawFetch(8),
      rawFetch(10, 9),
      rawFetch(6),
      rawFetch(7),
      rawFetch(2),
      rawFetch(11),
      rawFetch(5, 3),
      0.U(1.W)
    ),
    12
  )
  val cBImm = signExtend(Cat(rawFetch(12), rawFetch(6, 5), rawFetch(2), rawFetch(11, 10), rawFetch(4, 3), 0.U(1.W)), 9)
  val cCiImm = signExtend(Cat(rawFetch(12), rawFetch(6, 2)), 6)
  val cShamt = Cat(rawFetch(12), rawFetch(6, 2))

  val opcode = instr(6, 0)
  val rd = instr(11, 7)
  val funct3 = instr(14, 12)
  val rs1 = instr(19, 15)
  val rs2 = instr(24, 20)
  val funct7 = instr(31, 25)
  val rs1Val = Mux(rs1 === 0.U, 0.U, regs(rs1))
  val rs2Val = Mux(rs2 === 0.U, 0.U, regs(rs2))

  val decodeIllegal = WireDefault(cExpanded.illegal && isC)
  val trap = WireDefault(false.B)
  val trapCause = WireDefault(0.U(32.W))
  val trapValue = WireDefault(0.U(32.W))
  val rdWen = WireDefault(false.B)
  val rdValue = WireDefault(0.U(32.W))
  val nextPc = WireDefault(pc + instrLen)
  val memValid = WireDefault(false.B)
  val memWrite = WireDefault(false.B)
  val memAddr = WireDefault(0.U(32.W))
  val memSize = WireDefault(word)
  val memWdata = WireDefault(0.U(32.W))
  val memWstrb = WireDefault(0.U(4.W))
  val memAtomic = WireDefault(none)
  val csrValid = WireDefault(false.B)
  val csrAddr = WireDefault(instr(31, 20))
  val csrWrite = WireDefault(false.B)
  val csrWdata = WireDefault(0.U(32.W))
  val csrRdata = WireDefault(0.U(32.W))
  val isMret = WireDefault(false.B)
  val retired = WireDefault(true.B)

  val byteShift = Cat(memAddr(1, 0), 0.U(3.W))
  val halfShift = Cat(memAddr(1), 0.U(4.W))
  val storeByteData = (rs2Val(7, 0) << byteShift)(31, 0)
  val storeHalfData = (rs2Val(15, 0) << halfShift)(31, 0)
  val storeByteStrb = (1.U(4.W) << memAddr(1, 0))(3, 0)
  val storeHalfStrb = Mux(memAddr(1), "b1100".U(4.W), "b0011".U(4.W))

  val csrReadOnly = csrAddr(11, 10) === "b11".U
  val csrImplemented = WireDefault(false.B)
  csrRdata := 0.U
  switch(csrAddr) {
    is(CSR_CYCLE) { csrImplemented := true.B; csrRdata := mcycle(31, 0) }
    is(CSR_TIME) { csrImplemented := true.B; csrRdata := io.platform.mtime(31, 0) }
    is(CSR_INSTRET) { csrImplemented := true.B; csrRdata := minstret(31, 0) }
    is(CSR_CYCLEH) { csrImplemented := true.B; csrRdata := mcycle(63, 32) }
    is(CSR_TIMEH) { csrImplemented := true.B; csrRdata := io.platform.mtime(63, 32) }
    is(CSR_INSTRETH) { csrImplemented := true.B; csrRdata := minstret(63, 32) }
    is(CSR_MSTATUS) { csrImplemented := true.B; csrRdata := mstatus }
    is(CSR_MISA) { csrImplemented := true.B; csrRdata := MisaValue }
    is(CSR_MIE) { csrImplemented := true.B; csrRdata := mie }
    is(CSR_MTVEC) { csrImplemented := true.B; csrRdata := mtvec }
    is(CSR_MCOUNTEREN) { csrImplemented := true.B; csrRdata := mcounteren }
    is(CSR_MSTATUSH) { csrImplemented := true.B; csrRdata := mstatush }
    is(CSR_MCOUNTINHIBIT) { csrImplemented := true.B; csrRdata := mcountinhibit }
    is(CSR_MSCRATCH) { csrImplemented := true.B; csrRdata := mscratch }
    is(CSR_MEPC) { csrImplemented := true.B; csrRdata := mepc }
    is(CSR_MCAUSE) { csrImplemented := true.B; csrRdata := mcause }
    is(CSR_MTVAL) { csrImplemented := true.B; csrRdata := mtval }
    is(CSR_MIP) { csrImplemented := true.B; csrRdata := mipEffective }
    is(CSR_MCYCLE) { csrImplemented := true.B; csrRdata := mcycle(31, 0) }
    is(CSR_MINSTRET) { csrImplemented := true.B; csrRdata := minstret(31, 0) }
    is(CSR_MCYCLEH) { csrImplemented := true.B; csrRdata := mcycle(63, 32) }
    is(CSR_MINSTRETH) { csrImplemented := true.B; csrRdata := minstret(63, 32) }
    is(CSR_MVENDORID) { csrImplemented := true.B; csrRdata := 0.U }
    is(CSR_MARCHID) { csrImplemented := true.B; csrRdata := ArchitectureId }
    is(CSR_MIMPID) { csrImplemented := true.B; csrRdata := Implementation }
    is(CSR_MHARTID) { csrImplemented := true.B; csrRdata := params.hartId.U(32.W) }
  }

  def branchTarget(offset: UInt): UInt = pc + offset
  def misalignedLoad(addr: UInt, size: UInt): Bool =
    (size === half && addr(0)) || (size === word && addr(1, 0) =/= 0.U)
  def misalignedStore(addr: UInt, size: UInt): Bool = misalignedLoad(addr, size)

  when(rawFetch(15, 0) === 0.U || (!isC && rawFetch === 0.U)) {
    decodeIllegal := true.B
  }

  val knownOpcode = opcode === "b0110111".U || opcode === "b0010111".U || opcode === "b1101111".U ||
    opcode === "b1100111".U || opcode === "b1100011".U || opcode === "b0000011".U ||
    opcode === "b0100011".U || opcode === "b0010011".U || opcode === "b0110011".U ||
    opcode === "b0001111".U || opcode === "b1110011".U || opcode === "b0101111".U
  when(!knownOpcode) {
    decodeIllegal := true.B
  }

  switch(opcode) {
    is("b0110111".U) {
      rdWen := rd =/= 0.U
      rdValue := immU(instr)
    }
    is("b0010111".U) {
      rdWen := rd =/= 0.U
      rdValue := pc + immU(instr)
    }
    is("b1101111".U) {
      rdWen := rd =/= 0.U
      rdValue := pc + instrLen
      nextPc := pc + immJ(instr)
    }
    is("b1100111".U) {
      when(funct3 === "b000".U) {
        rdWen := rd =/= 0.U
        rdValue := pc + instrLen
        nextPc := (rs1Val + immI(instr)) & "hfffffffe".U
      }.otherwise { decodeIllegal := true.B }
    }
    is("b1100011".U) {
      val signedLt = rs1Val.asSInt < rs2Val.asSInt
      val unsignedLt = rs1Val < rs2Val
      val taken = MuxLookup(funct3, false.B)(
        Seq(
          "b000".U -> (rs1Val === rs2Val),
          "b001".U -> (rs1Val =/= rs2Val),
          "b100".U -> signedLt,
          "b101".U -> !signedLt,
          "b110".U -> unsignedLt,
          "b111".U -> !unsignedLt
        )
      )
      when(funct3 === "b010".U || funct3 === "b011".U) { decodeIllegal := true.B }
      nextPc := Mux(taken, branchTarget(immB(instr)), pc + instrLen)
    }
    is("b0000011".U) {
      memValid := true.B
      memAddr := rs1Val + immI(instr)
      memWrite := false.B
      memSize := MuxLookup(funct3, word)(
        Seq("b000".U -> byte, "b001".U -> half, "b010".U -> word, "b100".U -> byte, "b101".U -> half)
      )
      when(funct3 === "b011".U || funct3 === "b110".U || funct3 === "b111".U) { decodeIllegal := true.B }
      when(misalignedLoad(memAddr, memSize)) {
        trap := true.B
        trapCause := loadAddrMisaligned
        trapValue := memAddr
        memValid := false.B
      }
    }
    is("b0100011".U) {
      memValid := true.B
      memAddr := rs1Val + immS(instr)
      memWrite := true.B
      memSize := MuxLookup(funct3, word)(Seq("b000".U -> byte, "b001".U -> half, "b010".U -> word))
      memWdata := MuxLookup(memSize, rs2Val)(Seq(byte -> storeByteData, half -> storeHalfData, word -> rs2Val))
      memWstrb := MuxLookup(memSize, "b1111".U(4.W))(
        Seq(byte -> storeByteStrb, half -> storeHalfStrb, word -> "b1111".U(4.W))
      )
      when(funct3 =/= "b000".U && funct3 =/= "b001".U && funct3 =/= "b010".U) { decodeIllegal := true.B }
      when(misalignedStore(memAddr, memSize)) {
        trap := true.B
        trapCause := storeAddrMisaligned
        trapValue := memAddr
        memValid := false.B
      }
    }
    is("b0010011".U) {
      val shamt = instr(24, 20)
      rdWen := rd =/= 0.U
      rdValue := MuxLookup(funct3, 0.U)(
        Seq(
          "b000".U -> (rs1Val + immI(instr)),
          "b010".U -> (rs1Val.asSInt < immI(instr).asSInt).asUInt,
          "b011".U -> (rs1Val < immI(instr)).asUInt,
          "b100".U -> (rs1Val ^ immI(instr)),
          "b110".U -> (rs1Val | immI(instr)),
          "b111".U -> (rs1Val & immI(instr))
        )
      )
      when(funct3 === "b001".U) {
        when(funct7 === "b0000000".U) { rdValue := (rs1Val << shamt)(31, 0) }
          .elsewhen(funct7 === "b0110000".U && shamt === "b00000".U) { rdValue := clz(rs1Val) }
          .elsewhen(funct7 === "b0110000".U && shamt === "b00001".U) { rdValue := ctz(rs1Val) }
          .elsewhen(funct7 === "b0110000".U && shamt === "b00010".U) { rdValue := cpop(rs1Val) }
          .elsewhen(funct7 === "b0110000".U && shamt === "b00100".U) { rdValue := sext8(rs1Val) }
          .elsewhen(funct7 === "b0110000".U && shamt === "b00101".U) { rdValue := sext16(rs1Val) }
          .elsewhen(funct7 === "b0010100".U) { rdValue := rs1Val | (1.U(32.W) << shamt) }
          .elsewhen(funct7 === "b0100100".U) { rdValue := rs1Val & ~(1.U(32.W) << shamt) }
          .elsewhen(funct7 === "b0110100".U) { rdValue := rs1Val ^ (1.U(32.W) << shamt) }
          .otherwise { decodeIllegal := true.B }
      }.elsewhen(funct3 === "b101".U) {
        when(funct7 === "b0000000".U) { rdValue := rs1Val >> shamt }
          .elsewhen(funct7 === "b0100000".U) { rdValue := (rs1Val.asSInt >> shamt).asUInt }
          .elsewhen(funct7 === "b0110000".U) { rdValue := ror(rs1Val, shamt) }
          .elsewhen(funct7 === "b0010100".U && shamt === "b00111".U) { rdValue := orcB(rs1Val) }
          .elsewhen(funct7 === "b0110100".U && shamt === "b11000".U) { rdValue := rev8(rs1Val) }
          .elsewhen(funct7 === "b0100100".U) { rdValue := (rs1Val >> shamt)(0) }
          .otherwise { decodeIllegal := true.B }
      }.elsewhen(funct3 === "b001".U || funct3 === "b101".U) {
        decodeIllegal := true.B
      }
    }
    is("b0110011".U) {
      rdWen := rd =/= 0.U
      val shamt = rs2Val(4, 0)
      val divZero = rs2Val === 0.U
      val divOverflow = rs1Val === "h80000000".U && rs2Val === "hffffffff".U
      rdValue := 0.U
      when(funct7 === "b0000000".U) {
        rdValue := MuxLookup(funct3, 0.U)(
          Seq(
            "b000".U -> (rs1Val + rs2Val),
            "b001".U -> (rs1Val << shamt)(31, 0),
            "b010".U -> (rs1Val.asSInt < rs2Val.asSInt).asUInt,
            "b011".U -> (rs1Val < rs2Val).asUInt,
            "b100".U -> (rs1Val ^ rs2Val),
            "b101".U -> (rs1Val >> shamt),
            "b110".U -> (rs1Val | rs2Val),
            "b111".U -> (rs1Val & rs2Val)
          )
        )
      }.elsewhen(funct7 === "b0100000".U) {
        when(funct3 === "b000".U) { rdValue := rs1Val - rs2Val }
          .elsewhen(funct3 === "b101".U) { rdValue := (rs1Val.asSInt >> shamt).asUInt }
          .elsewhen(funct3 === "b100".U) { rdValue := ~(rs1Val ^ rs2Val) }
          .elsewhen(funct3 === "b110".U) { rdValue := rs1Val | ~rs2Val }
          .elsewhen(funct3 === "b111".U) { rdValue := rs1Val & ~rs2Val }
          .otherwise { decodeIllegal := true.B }
      }.elsewhen(funct7 === "b0000001".U) {
        val sMul = (rs1Val.asSInt * rs2Val.asSInt).asUInt
        val suMul = (rs1Val.asSInt * Cat(0.U(32.W), rs2Val).asSInt).asUInt
        val uMul = (rs1Val * rs2Val).asUInt
        rdValue := MuxLookup(funct3, 0.U)(
          Seq(
            "b000".U -> uMul(31, 0),
            "b001".U -> sMul(63, 32),
            "b010".U -> suMul(63, 32),
            "b011".U -> uMul(63, 32),
            "b100".U -> Mux(
              divZero,
              "hffffffff".U,
              Mux(divOverflow, "h80000000".U, (rs1Val.asSInt / rs2Val.asSInt).asUInt)
            ),
            "b101".U -> Mux(divZero, "hffffffff".U, rs1Val / rs2Val),
            "b110".U -> Mux(divZero, rs1Val, Mux(divOverflow, 0.U, (rs1Val.asSInt % rs2Val.asSInt).asUInt)),
            "b111".U -> Mux(divZero, rs1Val, rs1Val % rs2Val)
          )
        )
      }.elsewhen(funct7 === "b0010000".U) {
        when(funct3 === "b010".U) { rdValue := ((rs1Val << 1)(31, 0) + rs2Val)(31, 0) }
          .elsewhen(funct3 === "b100".U) { rdValue := ((rs1Val << 2)(31, 0) + rs2Val)(31, 0) }
          .elsewhen(funct3 === "b110".U) { rdValue := ((rs1Val << 3)(31, 0) + rs2Val)(31, 0) }
          .otherwise { decodeIllegal := true.B }
      }.elsewhen(funct7 === "b0000101".U) {
        when(funct3 === "b100".U) { rdValue := Mux(rs1Val.asSInt < rs2Val.asSInt, rs1Val, rs2Val) }
          .elsewhen(funct3 === "b101".U) { rdValue := Mux(rs1Val < rs2Val, rs1Val, rs2Val) }
          .elsewhen(funct3 === "b110".U) { rdValue := Mux(rs1Val.asSInt > rs2Val.asSInt, rs1Val, rs2Val) }
          .elsewhen(funct3 === "b111".U) { rdValue := Mux(rs1Val > rs2Val, rs1Val, rs2Val) }
          .otherwise { decodeIllegal := true.B }
      }.elsewhen(funct7 === "b0000100".U && funct3 === "b100".U && rs2 === 0.U) {
        rdValue := Cat(0.U(16.W), rs1Val(15, 0))
      }.elsewhen(funct7 === "b0110000".U) {
        when(funct3 === "b001".U) { rdValue := rol(rs1Val, shamt) }
          .elsewhen(funct3 === "b101".U) { rdValue := ror(rs1Val, shamt) }
          .otherwise { decodeIllegal := true.B }
      }.elsewhen(funct7 === "b0010100".U && funct3 === "b001".U) {
        rdValue := rs1Val | (1.U(32.W) << shamt)
      }.elsewhen(funct7 === "b0100100".U && funct3 === "b001".U) {
        rdValue := rs1Val & ~(1.U(32.W) << shamt)
      }.elsewhen(funct7 === "b0110100".U && funct3 === "b001".U) {
        rdValue := rs1Val ^ (1.U(32.W) << shamt)
      }.elsewhen(funct7 === "b0100100".U && funct3 === "b101".U) {
        rdValue := (rs1Val >> shamt)(0)
      }.otherwise {
        decodeIllegal := true.B
      }
    }
    is("b0001111".U) {
      when(funct3 =/= "b000".U && funct3 =/= "b001".U) { decodeIllegal := true.B }
    }
    is("b1110011".U) {
      when(funct3 === "b000".U) {
        switch(instr) {
          is("h00000073".U) {
            trap := true.B
            trapCause := ecallM
            trapValue := 0.U
          }
          is("h00100073".U) {
            trap := true.B
            trapCause := breakpoint
            trapValue := pc
          }
          is("h30200073".U) { isMret := true.B; retired := true.B }
          is("h10500073".U) { retired := true.B }
        }
        when(instr =/= "h00000073".U && instr =/= "h00100073".U && instr =/= "h30200073".U && instr =/= "h10500073".U) {
          decodeIllegal := true.B
        }
      }.otherwise {
        csrValid := true.B
        val zimm = rs1
        val old = csrRdata
        val readSuppressed = funct3 === "b001".U && rd === 0.U
        val writeSuppressed = (funct3 === "b010".U || funct3 === "b011".U) && rs1 === 0.U ||
          (funct3 === "b110".U || funct3 === "b111".U) && zimm === 0.U
        val writeData = WireDefault(rs1Val)
        when(funct3 === "b010".U) { writeData := old | rs1Val }
        when(funct3 === "b011".U) { writeData := old & ~rs1Val }
        when(funct3 === "b101".U) { writeData := zimm }
        when(funct3 === "b110".U) { writeData := old | zimm }
        when(funct3 === "b111".U) { writeData := old & ~zimm }
        when(
          funct3 === "b001".U || funct3 === "b010".U || funct3 === "b011".U ||
            funct3 === "b101".U || funct3 === "b110".U || funct3 === "b111".U
        ) {
          csrWrite := !writeSuppressed
          csrWdata := writeData
          rdWen := rd =/= 0.U
          rdValue := Mux(readSuppressed, 0.U, old)
          when(!csrImplemented || (csrReadOnly && !writeSuppressed)) {
            decodeIllegal := true.B
          }
        }.otherwise {
          decodeIllegal := true.B
        }
      }
    }
    is("b0101111".U) {
      val amoFunct5 = instr(31, 27)
      when(funct3 =/= "b010".U) {
        decodeIllegal := true.B
      }.otherwise {
        memValid := true.B
        memAddr := rs1Val
        memSize := word
        memWrite := amoFunct5 =/= "b00010".U
        memWdata := rs2Val
        memWstrb := "b1111".U
        memAtomic := MuxLookup(amoFunct5, none)(
          Seq(
            "b00010".U -> lr,
            "b00011".U -> sc,
            "b00001".U -> swap,
            "b00000".U -> add,
            "b00100".U -> xor,
            "b01100".U -> and,
            "b01000".U -> or,
            "b10000".U -> min,
            "b10100".U -> max,
            "b11000".U -> minu,
            "b11100".U -> maxu
          )
        )
        when(memAtomic === none) { decodeIllegal := true.B }
        when(memAtomic === lr && rs2 =/= 0.U) { decodeIllegal := true.B }
        when(memAddr(1, 0) =/= 0.U) {
          trap := true.B
          trapCause := Mux(memAtomic === lr, loadAddrMisaligned, storeAddrMisaligned)
          trapValue := memAddr
          memValid := false.B
        }
      }
    }
  }

  when(isC && cQuadrant === "b01".U && (cFunct3 === "b001".U || cFunct3 === "b101".U)) {
    decodeIllegal := false.B
    trap := false.B
    memValid := false.B
    csrValid := false.B
    rdWen := cFunct3 === "b001".U
    rdValue := pc + 2.U
    nextPc := pc + cJImm
  }

  when(isC && cQuadrant === "b01".U && (cFunct3 === "b110".U || cFunct3 === "b111".U)) {
    val taken = Mux(cFunct3 === "b110".U, cRs1pVal === 0.U, cRs1pVal =/= 0.U)
    decodeIllegal := false.B
    trap := false.B
    rdWen := false.B
    memValid := false.B
    csrValid := false.B
    nextPc := Mux(taken, pc + cBImm, pc + 2.U)
  }

  when(isC && cQuadrant === "b01".U && cFunct3 === "b100".U) {
    when(rawFetch(11, 10) === "b00".U && !rawFetch(12)) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := cShamt =/= 0.U
      rdValue := cRs1pVal >> cShamt(4, 0)
      nextPc := pc + 2.U
    }.elsewhen(rawFetch(11, 10) === "b01".U && !rawFetch(12)) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := cShamt =/= 0.U
      rdValue := (cRs1pVal.asSInt >> cShamt(4, 0)).asUInt
      nextPc := pc + 2.U
    }.elsewhen(rawFetch(11, 10) === "b10".U) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := true.B
      rdValue := cRs1pVal & cCiImm
      nextPc := pc + 2.U
    }.elsewhen(rawFetch(11, 10) === "b11".U && !rawFetch(12)) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := true.B
      rdValue := MuxLookup(rawFetch(6, 5), cRs1pVal - cRs2pVal)(
        Seq(
          "b00".U -> (cRs1pVal - cRs2pVal),
          "b01".U -> (cRs1pVal ^ cRs2pVal),
          "b10".U -> (cRs1pVal | cRs2pVal),
          "b11".U -> (cRs1pVal & cRs2pVal)
        )
      )
      nextPc := pc + 2.U
    }
  }

  when(isC && cQuadrant === "b10".U && cFunct3 === "b100".U) {
    when(!rawFetch(12) && cRs2 === 0.U) {
      decodeIllegal := cRd === 0.U
      trap := false.B
      rdWen := false.B
      memValid := false.B
      csrValid := false.B
      nextPc := cRdVal & "hfffffffe".U
    }.elsewhen(!rawFetch(12) && cRs2 =/= 0.U) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := cRd =/= 0.U
      rdValue := cRs2Val
      nextPc := pc + 2.U
    }.elsewhen(rawFetch(12) && cRd =/= 0.U && cRs2 === 0.U) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := true.B
      rdValue := pc + 2.U
      nextPc := cRdVal & "hfffffffe".U
    }.elsewhen(rawFetch(12) && cRd =/= 0.U && cRs2 =/= 0.U) {
      decodeIllegal := false.B
      trap := false.B
      memValid := false.B
      csrValid := false.B
      rdWen := true.B
      rdValue := cRdVal + cRs2Val
      nextPc := pc + 2.U
    }
  }

  when(decodeIllegal) {
    trap := true.B
    trapCause := illegalInstruction
    trapValue := Mux(isC, rawFetch(15, 0), instr)
    memValid := false.B
    csrValid := false.B
    rdWen := false.B
  }
  when(fetchFaultReg) {
    trap := true.B
    trapCause := instrAccessFault
    trapValue := pc
    memValid := false.B
    csrValid := false.B
    rdWen := false.B
  }
  when(pc(0)) {
    trap := true.B
    trapCause := instrAddrMisaligned
    trapValue := pc
    memValid := false.B
    csrValid := false.B
    rdWen := false.B
  }

  def trapVector(cause: UInt): UInt = {
    val base = Cat(mtvec(31, 2), 0.U(2.W))
    Mux(mtvec(1, 0) === 1.U && cause(31), base + Cat(0.U(26.W), cause(5, 0), 0.U(2.W)), base)
  }

  val traceValid = WireDefault(false.B)
  val trace = WireDefault(0.U.asTypeOf(new NovaV1RetireTrace))

  def fillTrace(
    retiredArg:   Bool,
    trapArg:      Bool,
    cause:        UInt,
    tval:         UInt,
    next:         UInt,
    rdWenArg:     Bool,
    rdValueArg:   UInt,
    memValidArg:  Bool,
    memWriteArg:  Bool,
    memAddrArg:   UInt,
    memSizeArg:   UInt,
    memWdataArg:  UInt,
    memRdataArg:  UInt,
    memFaultArg:  Bool,
    memAtomicArg: UInt,
    csrValidArg:  Bool,
    csrWriteArg:  Bool
  ): Unit = {
    traceValid := true.B
    trace.retired := retiredArg
    trace.pc := pc
    trace.instr := Mux(isC, Cat(0.U(16.W), rawFetch(15, 0)), instr)
    trace.instrLen := instrLen
    trace.nextPc := next
    trace.rd := rd
    trace.rdWen := rdWenArg
    trace.rdValue := rdValueArg
    trace.trap := trapArg
    trace.trapCause := cause
    trace.trapValue := tval
    trace.memValid := memValidArg
    trace.memWrite := memWriteArg
    trace.memAddr := memAddrArg
    trace.memSize := memSizeArg
    trace.memWdata := memWdataArg
    trace.memRdata := memRdataArg
    trace.memFault := memFaultArg
    trace.memAtomic := memAtomicArg
    trace.csrValid := csrValidArg
    trace.csrAddr := csrAddr
    trace.csrWrite := csrWriteArg
    trace.csrWdata := csrWdata
    trace.csrRdata := csrRdata
  }

  def fillMemoryTrace(
    retiredArg: Bool,
    trapArg:    Bool,
    cause:      UInt,
    tval:       UInt,
    rdWenArg:   Bool,
    rdValueArg: UInt,
    rdata:      UInt,
    fault:      Bool
  ): Unit = {
    traceValid := true.B
    trace.retired := retiredArg
    trace.pc := pc
    trace.instr := Mux(isC, Cat(0.U(16.W), rawFetch(15, 0)), instr)
    trace.instrLen := instrLen
    trace.nextPc := Mux(trapArg, trapVector(cause), nextPc)
    trace.rd := lsuRd
    trace.rdWen := rdWenArg
    trace.rdValue := rdValueArg
    trace.trap := trapArg
    trace.trapCause := cause
    trace.trapValue := tval
    trace.memValid := true.B
    trace.memWrite := lsuWrite
    trace.memAddr := lsuAddr
    trace.memSize := lsuSize
    trace.memWdata := Mux(lsuAtomic =/= none && lsuAtomic =/= lr && lsuAtomic =/= sc, amoWriteData, lsuWdata)
    trace.memRdata := rdata
    trace.memFault := fault
    trace.memAtomic := lsuAtomic
    trace.csrValid := false.B
  }

  if (params.enableTrace) {
    val tracePort = io.debug.retire
    val traceSkidValid = RegInit(false.B)
    val traceSkidBits = RegInit(0.U.asTypeOf(new NovaV1RetireTrace))
    val tracePortFire = tracePort.valid && tracePort.ready

    traceCanAccept := !traceSkidValid || tracePort.ready
    traceBlocked := traceSkidValid && !tracePort.ready
    tracePort.valid := traceSkidValid || traceValid
    tracePort.bits := Mux(traceSkidValid, traceSkidBits, trace)

    when(traceSkidValid) {
      when(tracePort.ready) {
        when(traceValid) {
          traceSkidBits := trace
        }.otherwise {
          traceSkidValid := false.B
        }
      }
    }.otherwise {
      when(traceValid && !tracePort.ready) {
        traceSkidValid := true.B
        traceSkidBits := trace
      }
    }

    assert(
      !(tracePortFire && tracePort.bits.retired && tracePort.bits.trap),
      "retired trace events must not also be trap events"
    )
    assert(!(tracePortFire && tracePort.bits.rdWen && tracePort.bits.rd === 0.U), "x0 writes are suppressed")
  } else {
    io.debug.retire.valid := false.B
    io.debug.retire.bits := 0.U.asTypeOf(new NovaV1RetireTrace)
  }

  def writeCsr(addr: UInt, data: UInt): Unit = {
    switch(addr) {
      is(CSR_MSTATUS) {
        val masked = data & "h00001888".U
        mstatus := masked.bitSet(MSTATUS_MPP.U, data(MSTATUS_MPP)).bitSet((MSTATUS_MPP + 1).U, data(MSTATUS_MPP + 1))
      }
      is(CSR_MSTATUSH) { mstatush := 0.U }
      is(CSR_MIE) { mie := data & "h00000888".U }
      is(CSR_MTVEC) { mtvec := Cat(data(31, 2), data(1, 0) & 1.U) }
      is(CSR_MCOUNTEREN) { mcounteren := data & "h00000007".U }
      is(CSR_MCOUNTINHIBIT) { mcountinhibit := data & "h00000025".U }
      is(CSR_MSCRATCH) { mscratch := data }
      is(CSR_MEPC) { mepc := data & "hfffffffe".U }
      is(CSR_MCAUSE) { mcause := data }
      is(CSR_MTVAL) { mtval := data }
      is(CSR_MIP) { mipSoftware := data & "h00000888".U }
      is(CSR_MCYCLE) { mcycle := Cat(mcycle(63, 32), data) }
      is(CSR_MCYCLEH) { mcycle := Cat(data, mcycle(31, 0)) }
      is(CSR_MINSTRET) { minstret := Cat(minstret(63, 32), data) }
      is(CSR_MINSTRETH) { minstret := Cat(data, minstret(31, 0)) }
    }
  }

  def enterTrap(cause: UInt, tval: UInt): Unit = {
    mepc := pc
    mcause := cause
    mtval := tval
    pc := trapVector(cause)
    trapSeen := true.B
    mstatus := mstatus
      .bitSet(MSTATUS_MPIE.U, mstatus(MSTATUS_MIE))
      .bitSet(MSTATUS_MIE.U, false.B)
      .bitSet(MSTATUS_MPP.U, true.B)
      .bitSet((MSTATUS_MPP + 1).U, true.B)
    state := Mux(haltRequested, sHalted, sFetch0Req)
  }

  def resetArchitectural(): Unit = {
    pc := bootPc
    state := sHalted
    regs := VecInit(Seq.fill(32)(0.U(32.W)))
    mstatus := 0.U
    mstatush := 0.U
    mie := 0.U
    mipSoftware := 0.U
    mtvec := 0.U
    mscratch := 0.U
    mepc := 0.U
    mcause := ResetCause
    mtval := 0.U
    mcounteren := 0.U
    mcountinhibit := 0.U
    mcycle := 0.U
    minstret := 0.U
    trapSeen := false.B
    haltRequested := false.B
    fetchWord0 := 0.U
    rawFetchReg := 0.U
    fetchFaultReg := false.B
    lsuAwPending := false.B
    lsuWPending := false.B
  }

  def loadResult(data: UInt): UInt = {
    val shiftByte = Cat(lsuAddr(1, 0), 0.U(3.W))
    val shiftHalf = Cat(lsuAddr(1), 0.U(4.W))
    val loadByte = (data >> shiftByte)(7, 0)
    val loadHalf = (data >> shiftHalf)(15, 0)
    MuxLookup(lsuSize, data)(
      Seq(
        byte -> Mux(lsuFunct3 === "b100".U, loadByte, sext8(loadByte)),
        half -> Mux(lsuFunct3 === "b101".U, loadHalf, sext16(loadHalf)),
        word -> data
      )
    )
  }

  def amoResult(old: UInt): UInt = {
    MuxLookup(lsuAtomic, lsuWdata)(
      Seq(
        swap -> lsuWdata,
        add -> (old + lsuWdata),
        xor -> (old ^ lsuWdata),
        and -> (old & lsuWdata),
        or -> (old | lsuWdata),
        min -> Mux(old.asSInt < lsuWdata.asSInt, old, lsuWdata),
        max -> Mux(old.asSInt > lsuWdata.asSInt, old, lsuWdata),
        minu -> Mux(old < lsuWdata, old, lsuWdata),
        maxu -> Mux(old > lsuWdata, old, lsuWdata)
      )
    )
  }

  def retireInstruction(finalNextPc: UInt, finalRdWen: Bool, finalRdValue: UInt): Unit = {
    pc := finalNextPc
    when(finalRdWen && rd =/= 0.U) { regs(rd) := finalRdValue }
    when(csrValid && csrWrite) { writeCsr(csrAddr, csrWdata) }
    when(!mcountinhibit(2) && retired) { minstret := minstret + 1.U }
    state := Mux(haltRequested, sHalted, sFetch0Req)
    regs(0) := 0.U
  }

  val storeReqDone = (state === sStoreReq || state === sScReq || state === sAmoWriteReq) &&
    (!lsuAwPending || io.mem.data.aw.fire) && (!lsuWPending || io.mem.data.w.fire)

  when(state =/= sHalted && !mcountinhibit(0)) {
    mcycle := mcycle + 1.U
  }

  when(commandSoftReset) {
    resetArchitectural()
  }.elsewhen(commandSetPc || commandResume) {
    when(commandSetPc) {
      pc := bootPc & "hfffffffe".U
      fetchFaultReg := false.B
    }
    when(commandResume) {
      haltRequested := false.B
      state := sFetch0Req
    }
  }.otherwise {
    switch(state) {
      is(sHalted) {}
      is(sFetch0Req) {
        when(interruptPending) {
          when(traceCanAccept) {
            traceValid := true.B
            trace.retired := false.B
            trace.pc := pc
            trace.instr := 0.U
            trace.instrLen := 0.U
            trace.nextPc := trapVector(interruptCause)
            trace.trap := true.B
            trace.trapCause := interruptCause
            enterTrap(interruptCause, 0.U)
          }
        }.elsewhen(pc(0)) {
          rawFetchReg := 0.U
          fetchFaultReg := false.B
          state := sExec
        }.elsewhen(io.mem.instruction.ar.fire) {
          state := sFetch0Resp
        }
      }
      is(sFetch0Resp) {
        when(io.mem.instruction.r.fire) {
          fetchWord0 := io.mem.instruction.r.bits.data
          fetchFaultReg := isError(io.mem.instruction.r.bits.resp)
          when(
            pc(1) && !isError(io.mem.instruction.r.bits.resp) && io.mem.instruction.r.bits.data(17, 16) === "b11".U
          ) {
            state := sFetch1Req
          }.otherwise {
            rawFetchReg := Mux(
              pc(1),
              Cat(0.U(16.W), io.mem.instruction.r.bits.data(31, 16)),
              io.mem.instruction.r.bits.data
            )
            state := sExec
          }
        }
      }
      is(sFetch1Req) {
        when(io.mem.instruction.ar.fire) {
          state := sFetch1Resp
        }
      }
      is(sFetch1Resp) {
        when(io.mem.instruction.r.fire) {
          rawFetchReg := Cat(io.mem.instruction.r.bits.data(15, 0), fetchWord0(31, 16))
          fetchFaultReg := isError(io.mem.instruction.r.bits.resp)
          state := sExec
        }
      }
      is(sExec) {
        when(trap) {
          when(traceCanAccept) {
            fillTrace(
              false.B,
              true.B,
              trapCause,
              trapValue,
              trapVector(trapCause),
              false.B,
              0.U,
              false.B,
              false.B,
              0.U,
              word,
              0.U,
              0.U,
              false.B,
              none,
              false.B,
              false.B
            )
            enterTrap(trapCause, trapValue)
          }
        }.elsewhen(isMret) {
          when(traceCanAccept) {
            fillTrace(
              true.B,
              false.B,
              0.U,
              0.U,
              mepc,
              false.B,
              0.U,
              false.B,
              false.B,
              0.U,
              word,
              0.U,
              0.U,
              false.B,
              none,
              false.B,
              false.B
            )
            pc := mepc
            mstatus := mstatus
              .bitSet(MSTATUS_MIE.U, mstatus(MSTATUS_MPIE))
              .bitSet(MSTATUS_MPIE.U, true.B)
              .bitSet(MSTATUS_MPP.U, true.B)
              .bitSet((MSTATUS_MPP + 1).U, true.B)
            when(!mcountinhibit(2)) { minstret := minstret + 1.U }
            state := Mux(haltRequested, sHalted, sFetch0Req)
          }
        }.elsewhen(memValid) {
          lsuAddr := memAddr
          lsuSize := memSize
          lsuWrite := memWrite
          lsuRd := rd
          lsuFunct3 := funct3
          lsuWdata := memWdata
          lsuWstrb := memWstrb
          lsuAtomic := memAtomic
          lsuAq := instr(26)
          lsuRl := instr(25)
          when(memAtomic === lr) {
            state := sLrReq
          }.elsewhen(memAtomic === sc) {
            lsuAwPending := true.B
            lsuWPending := true.B
            state := sScReq
          }.elsewhen(memAtomic =/= none) {
            state := sAmoReadReq
          }.elsewhen(memWrite) {
            lsuAwPending := true.B
            lsuWPending := true.B
            state := sStoreReq
          }.otherwise {
            state := sLoadReq
          }
        }.otherwise {
          when(traceCanAccept) {
            fillTrace(
              retired,
              false.B,
              0.U,
              0.U,
              nextPc,
              rdWen && rd =/= 0.U,
              rdValue,
              false.B,
              false.B,
              0.U,
              word,
              0.U,
              0.U,
              false.B,
              none,
              csrValid,
              csrWrite
            )
            retireInstruction(nextPc, rdWen, rdValue)
          }
        }
      }
      is(sLoadReq) {
        when(io.mem.data.ar.fire) {
          state := sLoadResp
        }
      }
      is(sLoadResp) {
        when(io.mem.data.r.fire) {
          val fault = isError(io.mem.data.r.bits.resp)
          val value = loadResult(io.mem.data.r.bits.data)
          fillMemoryTrace(
            !fault,
            fault,
            loadAccessFault,
            lsuAddr,
            lsuRd =/= 0.U && !fault,
            value,
            io.mem.data.r.bits.data,
            fault
          )
          when(fault) {
            enterTrap(loadAccessFault, lsuAddr)
          }.otherwise {
            when(lsuRd =/= 0.U) { regs(lsuRd) := value }
            pc := nextPc
            when(!mcountinhibit(2)) { minstret := minstret + 1.U }
            state := Mux(haltRequested, sHalted, sFetch0Req)
            regs(0) := 0.U
          }
        }
      }
      is(sStoreReq) {
        when(io.mem.data.aw.fire) { lsuAwPending := false.B }
        when(io.mem.data.w.fire) { lsuWPending := false.B }
        when(storeReqDone) {
          lsuAwPending := false.B
          lsuWPending := false.B
          state := sStoreResp
        }
      }
      is(sStoreResp) {
        when(io.mem.data.b.fire) {
          val fault = isError(io.mem.data.b.bits.resp)
          fillMemoryTrace(!fault, fault, storeAccessFault, lsuAddr, false.B, 0.U, 0.U, fault)
          when(fault) {
            enterTrap(storeAccessFault, lsuAddr)
          }.otherwise {
            pc := nextPc
            when(!mcountinhibit(2)) { minstret := minstret + 1.U }
            state := Mux(haltRequested, sHalted, sFetch0Req)
          }
        }
      }
      is(sLrReq) {
        when(io.mem.data.ar.fire) {
          state := sLrResp
        }
      }
      is(sLrResp) {
        when(io.mem.data.r.fire) {
          val fault = isError(io.mem.data.r.bits.resp)
          fillMemoryTrace(
            !fault,
            fault,
            loadAccessFault,
            lsuAddr,
            lsuRd =/= 0.U && !fault,
            io.mem.data.r.bits.data,
            io.mem.data.r.bits.data,
            fault
          )
          when(fault) {
            enterTrap(loadAccessFault, lsuAddr)
          }.otherwise {
            when(lsuRd =/= 0.U) { regs(lsuRd) := io.mem.data.r.bits.data }
            pc := nextPc
            when(!mcountinhibit(2)) { minstret := minstret + 1.U }
            state := Mux(haltRequested, sHalted, sFetch0Req)
            regs(0) := 0.U
          }
        }
      }
      is(sScReq) {
        when(io.mem.data.aw.fire) { lsuAwPending := false.B }
        when(io.mem.data.w.fire) { lsuWPending := false.B }
        when(storeReqDone) {
          lsuAwPending := false.B
          lsuWPending := false.B
          state := sScResp
        }
      }
      is(sScResp) {
        when(io.mem.data.b.fire) {
          val fault = isError(io.mem.data.b.bits.resp)
          val success = io.mem.data.b.bits.resp === exOkay
          val status = Mux(success, 0.U(32.W), 1.U(32.W))
          fillMemoryTrace(!fault, fault, storeAccessFault, lsuAddr, lsuRd =/= 0.U && !fault, status, status, fault)
          when(fault) {
            enterTrap(storeAccessFault, lsuAddr)
          }.otherwise {
            when(lsuRd =/= 0.U) { regs(lsuRd) := status }
            pc := nextPc
            when(!mcountinhibit(2)) { minstret := minstret + 1.U }
            state := Mux(haltRequested, sHalted, sFetch0Req)
            regs(0) := 0.U
          }
        }
      }
      is(sAmoReadReq) {
        when(io.mem.data.ar.fire) {
          state := sAmoReadResp
        }
      }
      is(sAmoReadResp) {
        when(io.mem.data.r.fire) {
          when(isError(io.mem.data.r.bits.resp)) {
            fillMemoryTrace(false.B, true.B, loadAccessFault, lsuAddr, false.B, 0.U, io.mem.data.r.bits.data, true.B)
            enterTrap(loadAccessFault, lsuAddr)
          }.otherwise {
            amoOld := io.mem.data.r.bits.data
            amoWriteData := amoResult(io.mem.data.r.bits.data)
            lsuAwPending := true.B
            lsuWPending := true.B
            state := sAmoWriteReq
          }
        }
      }
      is(sAmoWriteReq) {
        when(io.mem.data.aw.fire) { lsuAwPending := false.B }
        when(io.mem.data.w.fire) { lsuWPending := false.B }
        when(storeReqDone) {
          lsuAwPending := false.B
          lsuWPending := false.B
          state := sAmoWriteResp
        }
      }
      is(sAmoWriteResp) {
        when(io.mem.data.b.fire) {
          when(isError(io.mem.data.b.bits.resp)) {
            fillMemoryTrace(false.B, true.B, storeAccessFault, lsuAddr, false.B, 0.U, amoOld, true.B)
            enterTrap(storeAccessFault, lsuAddr)
          }.elsewhen(io.mem.data.b.bits.resp =/= exOkay) {
            state := sAmoReadReq
          }.otherwise {
            fillMemoryTrace(true.B, false.B, 0.U, 0.U, lsuRd =/= 0.U, amoOld, amoOld, false.B)
            when(lsuRd =/= 0.U) { regs(lsuRd) := amoOld }
            pc := nextPc
            when(!mcountinhibit(2)) { minstret := minstret + 1.U }
            state := Mux(haltRequested, sHalted, sFetch0Req)
            regs(0) := 0.U
          }
        }
      }
    }
  }

  when(commandClearTrap && !commandSoftReset) {
    trapSeen := false.B
  }

  when(commandHalt && !commandSoftReset && !commandResume) {
    haltRequested := true.B
    when(state === sFetch0Req || state === sHalted) {
      state := sHalted
    }
  }

  assert(!(traceValid && trace.retired && trace.trap), "retired trace events must not also be trap events")
  assert(!(traceValid && trace.rdWen && trace.rd === 0.U), "x0 writes are suppressed")
}
