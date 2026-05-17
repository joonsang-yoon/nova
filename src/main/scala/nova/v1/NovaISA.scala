package nova.v1

import chisel3._
import chisel3.util._

object NovaISA {
  def signExtend(value: UInt, width: Int): UInt = {
    require(width > 0 && width <= 32)
    Cat(Fill(32 - width, value(width - 1)), value(width - 1, 0))
  }

  def immI(i: UInt): UInt = signExtend(i(31, 20), 12)
  def immS(i: UInt): UInt = signExtend(Cat(i(31, 25), i(11, 7)), 12)
  def immB(i: UInt): UInt = signExtend(Cat(i(31), i(7), i(30, 25), i(11, 8), 0.U(1.W)), 13)
  def immU(i: UInt): UInt = Cat(i(31, 12), 0.U(12.W))
  def immJ(i: UInt): UInt = signExtend(Cat(i(31), i(19, 12), i(20), i(30, 21), 0.U(1.W)), 21)

  def sext8(x:  UInt): UInt = signExtend(x(7, 0), 8)
  def sext16(x: UInt): UInt = signExtend(x(15, 0), 16)

  def clz(x:  UInt): UInt = Mux(x === 0.U, 32.U, PriorityEncoder(Reverse(x))).asUInt
  def ctz(x:  UInt): UInt = Mux(x === 0.U, 32.U, PriorityEncoder(x)).asUInt
  def cpop(x: UInt): UInt = PopCount(x).asUInt

  def orcB(x: UInt): UInt = Cat(
    Mux(x(31, 24) === 0.U, 0.U(8.W), "hff".U(8.W)),
    Mux(x(23, 16) === 0.U, 0.U(8.W), "hff".U(8.W)),
    Mux(x(15, 8) === 0.U, 0.U(8.W), "hff".U(8.W)),
    Mux(x(7, 0) === 0.U, 0.U(8.W), "hff".U(8.W))
  )

  def rev8(x: UInt): UInt = Cat(x(7, 0), x(15, 8), x(23, 16), x(31, 24))

  def rol(x: UInt, shamt: UInt): UInt = {
    val s = shamt(4, 0)
    ((x << s)(31, 0) | (x >> (32.U - s))(31, 0)).asUInt
  }

  def ror(x: UInt, shamt: UInt): UInt = {
    val s = shamt(4, 0)
    ((x >> s) | (x << (32.U - s))(31, 0)).asUInt
  }

  private def reg5(x: UInt): UInt = x.pad(5)(4, 0)

  def addi(rd: UInt, rs1: UInt, imm: UInt): UInt =
    Cat(imm(11, 0), reg5(rs1), "b000".U(3.W), reg5(rd), "b0010011".U(7.W))
  def lui(rd: UInt, imm: UInt):            UInt = Cat(imm(31, 12), reg5(rd), "b0110111".U(7.W))
  def lw(rd:  UInt, rs1: UInt, imm: UInt): UInt = Cat(imm(11, 0), reg5(rs1), "b010".U(3.W), reg5(rd), "b0000011".U(7.W))
  def sw(rs2: UInt, rs1: UInt, imm: UInt): UInt =
    Cat(imm(11, 5), reg5(rs2), reg5(rs1), "b010".U(3.W), imm(4, 0), "b0100011".U(7.W))
  def op(rd: UInt, rs1: UInt, rs2: UInt, funct3: UInt, funct7: UInt): UInt =
    Cat(funct7, reg5(rs2), reg5(rs1), funct3, reg5(rd), "b0110011".U(7.W))
  def jal(rd: UInt, imm: UInt): UInt =
    Cat(imm(20), imm(10, 1), imm(11), imm(19, 12), reg5(rd), "b1101111".U(7.W))
  def jalr(rd: UInt, rs1: UInt): UInt = Cat(0.U(12.W), reg5(rs1), "b000".U(3.W), reg5(rd), "b1100111".U(7.W))
  def branch(rs1: UInt, rs2: UInt, funct3: UInt, imm: UInt): UInt =
    Cat(imm(12), imm(10, 5), reg5(rs2), reg5(rs1), funct3, imm(4, 1), imm(11), "b1100011".U(7.W))
  def srli(rd: UInt, rs1: UInt, shamt: UInt): UInt =
    Cat("b0000000".U(7.W), shamt(4, 0), reg5(rs1), "b101".U(3.W), reg5(rd), "b0010011".U(7.W))
  def srai(rd: UInt, rs1: UInt, shamt: UInt): UInt =
    Cat("b0100000".U(7.W), shamt(4, 0), reg5(rs1), "b101".U(3.W), reg5(rd), "b0010011".U(7.W))
  def andi(rd: UInt, rs1: UInt, imm: UInt): UInt =
    Cat(imm(11, 0), reg5(rs1), "b111".U(3.W), reg5(rd), "b0010011".U(7.W))
  def slli(rd: UInt, rs1: UInt, shamt: UInt): UInt =
    Cat("b0000000".U(7.W), shamt(4, 0), reg5(rs1), "b001".U(3.W), reg5(rd), "b0010011".U(7.W))
  def ebreak: UInt = "h00100073".U(32.W)

  final class CDecode extends Bundle {
    val instr = UInt(32.W)
    val illegal = Bool()
  }

  def expandCompressed(c: UInt): CDecode = {
    val out = Wire(new CDecode)
    val q = c(1, 0)
    val funct3 = c(15, 13)
    val rd = c(11, 7)
    val rs2 = c(6, 2)
    val rdp = Cat("b01".U(2.W), c(4, 2))
    val rs1p = Cat("b01".U(2.W), c(9, 7))
    val rs2p = Cat("b01".U(2.W), c(4, 2))
    val ciImm = signExtend(Cat(c(12), c(6, 2)), 6)
    val cjImm = signExtend(Cat(c(12), c(8), c(10, 9), c(6), c(7), c(2), c(11), c(5, 3), 0.U(1.W)), 12)
    val cbImm = signExtend(Cat(c(12), c(6, 5), c(2), c(11, 10), c(4, 3), 0.U(1.W)), 9)
    val uimmLw = Cat(0.U(25.W), c(5), c(12, 10), c(6), 0.U(2.W))
    val uimmSpL = Cat(0.U(24.W), c(3, 2), c(12), c(6, 4), 0.U(2.W))
    val uimmSpS = Cat(0.U(24.W), c(8, 7), c(12, 9), 0.U(2.W))
    val addi4sp = Cat(0.U(22.W), c(10, 7), c(12, 11), c(5), c(6), 0.U(2.W))
    val addi16 = signExtend(Cat(c(12), c(4, 3), c(5), c(2), c(6), 0.U(4.W)), 10)
    val luiImm = signExtend(Cat(c(12), c(6, 2), 0.U(12.W)), 18)
    val shamt = Cat(c(12), c(6, 2))

    out.instr := "h00000013".U // ADDI x0, x0, 0
    out.illegal := false.B

    switch(q) {
      is("b00".U) {
        when(funct3 =/= "b000".U && funct3 =/= "b010".U && funct3 =/= "b110".U) {
          out.illegal := true.B
        }
        switch(funct3) {
          is("b000".U) {
            out.instr := addi(rdp, 2.U, addi4sp)
            out.illegal := addi4sp === 0.U
          }
          is("b010".U) { out.instr := lw(rdp, rs1p, uimmLw) }
          is("b110".U) { out.instr := sw(rs2p, rs1p, uimmLw) }
        }
      }
      is("b01".U) {
        switch(funct3) {
          is("b000".U) {
            when(rd =/= 0.U && ciImm =/= 0.U) {
              out.instr := addi(rd, rd, ciImm)
            }
          }
          is("b001".U) { out.instr := jal(1.U, cjImm) }
          is("b010".U) {
            when(rd =/= 0.U) {
              out.instr := addi(rd, 0.U, ciImm)
            }
          }
          is("b011".U) {
            when(rd === 2.U) {
              out.instr := addi(2.U, 2.U, addi16)
              out.illegal := addi16 === 0.U
            }.elsewhen(rd === 0.U) {
              out.illegal := luiImm === 0.U
            }.otherwise {
              out.instr := lui(rd, luiImm)
              out.illegal := luiImm === 0.U
            }
          }
          is("b100".U) {
            when(c(11, 10) === "b00".U) {
              out.illegal := c(12)
              when(!c(12) && shamt =/= 0.U) {
                out.instr := srli(rs1p, rs1p, shamt)
              }
            }.elsewhen(c(11, 10) === "b01".U) {
              out.illegal := c(12)
              when(!c(12) && shamt =/= 0.U) {
                out.instr := srai(rs1p, rs1p, shamt)
              }
            }.elsewhen(c(11, 10) === "b10".U) {
              out.instr := andi(rs1p, rs1p, ciImm)
            }.otherwise {
              out.illegal := c(12)
              switch(c(6, 5)) {
                is("b00".U) { out.instr := op(rs1p, rs1p, rs2p, "b000".U, "b0100000".U) } // SUB
                is("b01".U) { out.instr := op(rs1p, rs1p, rs2p, "b100".U, "b0000000".U) } // XOR
                is("b10".U) { out.instr := op(rs1p, rs1p, rs2p, "b110".U, "b0000000".U) } // OR
                is("b11".U) { out.instr := op(rs1p, rs1p, rs2p, "b111".U, "b0000000".U) } // AND
              }
            }
          }
          is("b101".U) { out.instr := jal(0.U, cjImm) }
          is("b110".U) { out.instr := branch(rs1p, 0.U, "b000".U, cbImm) }
          is("b111".U) { out.instr := branch(rs1p, 0.U, "b001".U, cbImm) }
        }
      }
      is("b10".U) {
        when(funct3 =/= "b000".U && funct3 =/= "b010".U && funct3 =/= "b100".U && funct3 =/= "b110".U) {
          out.illegal := true.B
        }
        switch(funct3) {
          is("b000".U) {
            out.illegal := c(12)
            when(!c(12) && rd =/= 0.U && shamt =/= 0.U) {
              out.instr := slli(rd, rd, shamt)
            }
          }
          is("b010".U) {
            out.instr := lw(rd, 2.U, uimmSpL)
            out.illegal := rd === 0.U
          }
          is("b100".U) {
            when(!c(12) && rs2 === 0.U) {
              out.instr := jalr(0.U, rd)
              out.illegal := rd === 0.U
            }.elsewhen(!c(12) && rs2 =/= 0.U) {
              out.instr := addi(rd, rs2, 0.U)
            }.elsewhen(c(12) && rd === 0.U && rs2 === 0.U) {
              out.instr := ebreak
            }.elsewhen(c(12) && rs2 === 0.U) {
              out.instr := jalr(1.U, rd)
            }.otherwise {
              out.instr := op(rd, rd, rs2, "b000".U, "b0000000".U)
            }
          }
          is("b110".U) { out.instr := sw(rs2, 2.U, uimmSpS) }
        }
      }
    }
    out
  }
}
