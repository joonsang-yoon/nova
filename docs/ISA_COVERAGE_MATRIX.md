# ISA Coverage Matrix

Source documents: `riscv-unprivileged.pdf` and `riscv-privileged.pdf`, Version `20260120`.

Legend: `NovaV1ExecutionSpec` rows are exercised through the AXI-backed ChiselSim directed execution suite. `masked-pass` means the row is covered by a grouped directed ELF and the Nova trace was compared against Spike commit logs with only documented HTIF, CLINT/timer, or EEI trap masks. `pass` means the row is covered by Nova directed or assertion evidence without a Spike-only mask.

| Mnemonic | Ext | Encoding Pattern | PDF Ref | Decoder | Directed Test | Random/Diff | Spike | One-Cycle |
|---|---|---|---|---|---|---|---|---|
| LUI | RV32I | `0110111` | Unpriv sec 2.4 p.27 | `NovaV1.scala` OP-U | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AUIPC | RV32I | `0010111` | Unpriv sec 2.4 p.27 | OP-U | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| JAL | RV32I | `1101111` | Unpriv sec 2.5 p.30 | OP-J | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| JALR | RV32I | `1100111/000` | Unpriv sec 2.5 p.30 | OP-I | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BEQ | RV32I | `1100011/000` | Unpriv sec 2.5 p.31 | branch | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BNE | RV32I | `1100011/001` | Unpriv sec 2.5 p.31 | branch | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BLT | RV32I | `1100011/100` | Unpriv sec 2.5 p.31 | branch | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BGE | RV32I | `1100011/101` | Unpriv sec 2.5 p.31 | branch | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BLTU | RV32I | `1100011/110` | Unpriv sec 2.5 p.31 | branch | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BGEU | RV32I | `1100011/111` | Unpriv sec 2.5 p.31 | branch | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| LB | RV32I | `0000011/000` | Unpriv sec 2.6 p.33 | load | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| LH | RV32I | `0000011/001` | Unpriv sec 2.6 p.33 | load | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| LW | RV32I | `0000011/010` | Unpriv sec 2.6 p.33 | load | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| LBU | RV32I | `0000011/100` | Unpriv sec 2.6 p.33 | load | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| LHU | RV32I | `0000011/101` | Unpriv sec 2.6 p.33 | load | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SB | RV32I | `0100011/000` | Unpriv sec 2.6 p.33 | store | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SH | RV32I | `0100011/001` | Unpriv sec 2.6 p.33 | store | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SW | RV32I | `0100011/010` | Unpriv sec 2.6 p.33 | store | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ADDI | RV32I | `0010011/000` | Unpriv sec 2.4 p.28 | op-imm | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SLTI | RV32I | `0010011/010` | Unpriv sec 2.4 p.28 | op-imm | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SLTIU | RV32I | `0010011/011` | Unpriv sec 2.4 p.28 | op-imm | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| XORI | RV32I | `0010011/100` | Unpriv sec 2.4 p.28 | op-imm | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ORI | RV32I | `0010011/110` | Unpriv sec 2.4 p.28 | op-imm | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ANDI | RV32I | `0010011/111` | Unpriv sec 2.4 p.28 | op-imm | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SLLI | RV32I | `0010011/001/0000000` | Unpriv sec 2.4 p.28 | op-imm shift | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SRLI | RV32I | `0010011/101/0000000` | Unpriv sec 2.4 p.28 | op-imm shift | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SRAI | RV32I | `0010011/101/0100000` | Unpriv sec 2.4 p.28 | op-imm shift | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ADD | RV32I | `0110011/000/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SUB | RV32I | `0110011/000/0100000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SLL | RV32I | `0110011/001/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SLT | RV32I | `0110011/010/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SLTU | RV32I | `0110011/011/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| XOR | RV32I | `0110011/100/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SRL | RV32I | `0110011/101/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SRA | RV32I | `0110011/101/0100000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| OR | RV32I | `0110011/110/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AND | RV32I | `0110011/111/0000000` | Unpriv sec 2.4 p.29 | op | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| FENCE | RV32I | `0001111/000` | Unpriv sec 2.7 p.35 | fence | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ECALL | RV32I | `00000073` | Unpriv sec 2.8 p.36 | system | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| EBREAK | RV32I | `00100073` | Unpriv sec 2.8 p.36 | system | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MUL | M | `0110011/000/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MULH | M | `0110011/001/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MULHSU | M | `0110011/010/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MULHU | M | `0110011/011/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| DIV | M | `0110011/100/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| DIVU | M | `0110011/101/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| REM | M | `0110011/110/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| REMU | M | `0110011/111/0000001` | Unpriv sec 12 p.65 | op-m | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| LR.W | A | `0101111/010/funct5=00010` | Unpriv sec 13 p.67 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SC.W | A | `0101111/010/funct5=00011` | Unpriv sec 13 p.67 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOSWAP.W | A | `funct5=00001` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOADD.W | A | `funct5=00000` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOXOR.W | A | `funct5=00100` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOAND.W | A | `funct5=01100` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOOR.W | A | `funct5=01000` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOMIN.W | A | `funct5=10000` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOMAX.W | A | `funct5=10100` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOMINU.W | A | `funct5=11000` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| AMOMAXU.W | A | `funct5=11100` | Unpriv sec 13.4 p.72 | amo | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.ADDI4SPN | C | `q0/f3=000` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.LW | C | `q0/f3=010` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.SW | C | `q0/f3=110` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.NOP | C | `q1/f3=000/rd=x0` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.ADDI | C | `q1/f3=000` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.JAL | C | `q1/f3=001` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.LI | C | `q1/f3=010` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.ADDI16SP | C | `q1/f3=011/rd=x2` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.LUI | C | `q1/f3=011` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.SRLI | C | `q1/f3=100/00` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.SRAI | C | `q1/f3=100/01` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.ANDI | C | `q1/f3=100/10` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.SUB | C | `q1/f3=100/11/00` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.XOR | C | `q1/f3=100/11/01` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.OR | C | `q1/f3=100/11/10` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.AND | C | `q1/f3=100/11/11` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.J | C | `q1/f3=101` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.BEQZ | C | `q1/f3=110` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.BNEZ | C | `q1/f3=111` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.SLLI | C | `q2/f3=000` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.LWSP | C | `q2/f3=010` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.JR | C | `q2/f3=100/rs2=0` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.MV | C | `q2/f3=100` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.EBREAK | C | `q2/f3=100/rd=rs2=0` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.JALR | C | `q2/f3=100` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.ADD | C | `q2/f3=100` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| C.SWSP | C | `q2/f3=110` | Unpriv sec 28 p.152 | `expandCompressed` | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CSRRW | Zicsr | `1110011/001` | Unpriv sec 6 p.47 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CSRRS | Zicsr | `1110011/010` | Unpriv sec 6 p.47 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CSRRC | Zicsr | `1110011/011` | Unpriv sec 6 p.47 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CSRRWI | Zicsr | `1110011/101` | Unpriv sec 6 p.47 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CSRRSI | Zicsr | `1110011/110` | Unpriv sec 6 p.47 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CSRRCI | Zicsr | `1110011/111` | Unpriv sec 6 p.47 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| FENCE.I | Zifencei | `0001111/001` | Unpriv sec 5 p.45 | fence | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CYCLE/CYCLEH | Zicntr | CSR `c00/c80` | Unpriv sec 7 p.51 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| TIME/TIMEH | Zicntr | CSR `c01/c81` | Unpriv sec 7 p.51 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| INSTRET/INSTRETH | Zicntr | CSR `c02/c82` | Unpriv sec 7 p.51 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SH1ADD | Zba | `0110011/010/0010000` | Unpriv sec 30.2 p.223 | op-zba | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SH2ADD | Zba | `0110011/100/0010000` | Unpriv sec 30.2 p.223 | op-zba | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SH3ADD | Zba | `0110011/110/0010000` | Unpriv sec 30.2 p.223 | op-zba | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ANDN | Zbb | `0110011/111/0100000` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ORN | Zbb | `0110011/110/0100000` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| XNOR | Zbb | `0110011/100/0100000` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CLZ | Zbb | `0010011/001/0110000/00000` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CTZ | Zbb | `0010011/001/0110000/00001` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| CPOP | Zbb | `0010011/001/0110000/00010` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MIN | Zbb | `0110011/100/0000101` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MINU | Zbb | `0110011/101/0000101` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MAX | Zbb | `0110011/110/0000101` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MAXU | Zbb | `0110011/111/0000101` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SEXT.B | Zbb | `0010011/001/0110000/00100` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| SEXT.H | Zbb | `0010011/001/0110000/00101` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ZEXT.H | Zbb | `0110011/100/0000100/rs2=00000` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ROL | Zbb | `0110011/001/0110000` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ROR | Zbb | `0110011/101/0110000` | Unpriv sec 30.3 p.223 | op-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| RORI | Zbb | `0010011/101/0110000` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| ORC.B | Zbb | `0010011/101/0010100/00111` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| REV8 | Zbb | `0010011/101/0110100/11000` | Unpriv sec 30.3 p.223 | op-imm-zbb | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BCLR | Zbs | `0110011/001/0100100` | Unpriv sec 30.5 p.226 | op-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BCLRI | Zbs | `0010011/001/0100100` | Unpriv sec 30.5 p.226 | op-imm-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BEXT | Zbs | `0110011/101/0100100` | Unpriv sec 30.5 p.226 | op-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BEXTI | Zbs | `0010011/101/0100100` | Unpriv sec 30.5 p.226 | op-imm-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BINV | Zbs | `0110011/001/0110100` | Unpriv sec 30.5 p.226 | op-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BINVI | Zbs | `0010011/001/0110100` | Unpriv sec 30.5 p.226 | op-imm-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BSET | Zbs | `0110011/001/0010100` | Unpriv sec 30.5 p.226 | op-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| BSETI | Zbs | `0010011/001/0010100` | Unpriv sec 30.5 p.226 | op-imm-zbs | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MRET | Priv | `30200073` | Priv sec 3 p.28 | system | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| WFI | Priv | `10500073` | Priv sec 3 p.28 | system | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MCYCLE/MCYCLEH | Priv | CSR `b00/b80` | Priv sec 3.1 p.28 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
| MINSTRET/MINSTRETH | Priv | CSR `b02/b82` | Priv sec 3.1 p.28 | csr | `NovaV1ExecutionSpec` | masked-pass | masked-pass | asserted |
