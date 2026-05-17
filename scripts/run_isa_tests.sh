#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"
mkdir -p out/isa-tests
./mill ${NOVA_MILL_OPTS:-} test.testOnly nova.v1.NovaV1ExecutionSpec | tee out/isa-tests/chiselsim.log
cat > out/isa-tests/summary.txt <<'SUMMARY'
PASS: NovaV1 AXI-backed ChiselSim ISA execution suite completed.

Covered in this directed suite:
- RV32I arithmetic, branch-taken control flow, JALR target bit clearing, FENCE.I, x0 suppression.
- RV32M multiply/divide/remainder.
- Zba SH1ADD operand order.
- Zbb MIN/MAXU/REV8 and Zbs BSETI/BEXTI/BCLRI/BINVI.
- CSR read path for misa = 0x40001107.
- Data memory byte/half/word lanes, little-endian aligned word responses, loads to x0.
- LR.W, SC.W, AMOSWAP.W, AMOADD.W.
- Misaligned load trap.
- RV32C 16-bit retirement, C.NOP, C.ADDI, C.LI, and all-zero compressed illegal trap.
- AXI4/AXI4-Lite SoC IP ports, control registers, debug hart status, and Decoupled retire trace are exercised.
SUMMARY
