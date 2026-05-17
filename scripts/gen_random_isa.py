#!/usr/bin/env python3
import argparse
import random
from pathlib import Path

OPS = [
    "add {rd}, {rs1}, {rs2}",
    "sub {rd}, {rs1}, {rs2}",
    "xor {rd}, {rs1}, {rs2}",
    "or {rd}, {rs1}, {rs2}",
    "and {rd}, {rs1}, {rs2}",
    "mul {rd}, {rs1}, {rs2}",
    "sll {rd}, {rs1}, {rs2}",
    "srl {rd}, {rs1}, {rs2}",
    "sra {rd}, {rs1}, {rs2}",
    "andn {rd}, {rs1}, {rs2}",
    "orn {rd}, {rs1}, {rs2}",
    "xnor {rd}, {rs1}, {rs2}",
    "rol {rd}, {rs1}, {rs2}",
    "ror {rd}, {rs1}, {rs2}",
    "min {rd}, {rs1}, {rs2}",
    "max {rd}, {rs1}, {rs2}",
    "minu {rd}, {rs1}, {rs2}",
    "maxu {rd}, {rs1}, {rs2}",
    "bset {rd}, {rs1}, {rs2}",
    "bclr {rd}, {rs1}, {rs2}",
    "binv {rd}, {rs1}, {rs2}",
    "bext {rd}, {rs1}, {rs2}",
    "addi {rd}, {rs1}, {imm12}",
    "xori {rd}, {rs1}, {imm12}",
    "ori {rd}, {rs1}, {imm12}",
    "andi {rd}, {rs1}, {imm12}",
    "slli {rd}, {rs1}, {shamt}",
    "srli {rd}, {rs1}, {shamt}",
    "srai {rd}, {rs1}, {shamt}",
    "rori {rd}, {rs1}, {shamt}",
    "bseti {rd}, {rs1}, {shamt}",
    "bclri {rd}, {rs1}, {shamt}",
    "binvi {rd}, {rs1}, {shamt}",
    "bexti {rd}, {rs1}, {shamt}",
]

CORPUS = [0, 1, -1, -2147483648, 2147483647, 0x12345678, 0x80000001]


def reg(n):
    return f"x{n}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--count", type=int, default=96)
    args = parser.parse_args()

    seed = int(args.seed, 0)
    rng = random.Random(seed)
    regs = list(range(1, 24))
    lines = [
        '    .include "sim/isa/test_macros.S"',
        "    .option push",
        "    .option norvc",
        "    TEST_START",
        f"    # deterministic seed 0x{seed & 0xffffffff:08x}",
    ]
    for idx, r in enumerate(regs):
        value = CORPUS[idx % len(CORPUS)] ^ ((seed + idx * 0x9E3779B9) & 0xFFFFFFFF)
        if value & 0x80000000:
            value -= 0x100000000
        lines.append(f"    li {reg(r)}, {value}")
    for _ in range(args.count):
        rd = reg(rng.choice(regs))
        rs1 = reg(rng.choice(regs))
        rs2 = reg(rng.choice(regs))
        imm = rng.randint(-2048, 2047)
        shamt = rng.randint(0, 31)
        op = rng.choice(OPS)
        lines.append(f"    {op.format(rd=rd, rs1=rs1, rs2=rs2, imm12=imm, shamt=shamt)}")
    lines += [
        "    PASS",
        "    .option pop",
        "    TEST_DATA",
        "",
    ]
    path = Path(args.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
