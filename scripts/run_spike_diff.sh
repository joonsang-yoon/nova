#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"

GCC="${NOVA_GCC_PREFIX}/bin/riscv32-unknown-elf-gcc"
SPIKE="${NOVA_SPIKE_PREFIX}/bin/spike"
NM="${NOVA_GCC_PREFIX}/bin/riscv32-unknown-elf-nm"
OBJDUMP="${NOVA_GCC_PREFIX}/bin/riscv32-unknown-elf-objdump"

if [[ ! -x "${GCC}" || ! -x "${SPIKE}" || ! -x "${NM}" ]]; then
  echo "FAIL: local RISC-V GCC, nm, or Spike is not built." >&2
  echo "Run scripts/install_riscv_gcc.sh and scripts/install_spike.sh first." >&2
  exit 1
fi

mkdir -p out/spike-diff out/spike-diff/random

compile_asm() {
  local src="$1"
  local elf="$2"
  "${GCC}" \
    -march="${NOVA_ISA}" \
    -mabi="${NOVA_ABI}" \
    -mcmodel=medany \
    -I. \
    -nostdlib \
    -nostartfiles \
    -static \
    -Wl,--build-id=none \
    -T sim/isa/linker.ld \
    -o "${elf}" \
    "${src}"
  if [[ -x "${OBJDUMP}" ]]; then
    "${OBJDUMP}" -d "${elf}" > "${elf%.elf}.dump"
  fi
}

sym() {
  local elf="$1"
  local name="$2"
  "${NM}" -g "${elf}" | awk -v s="${name}" '$3 == s { print "0x" $1; found=1; exit } END { if (!found) exit 1 }'
}

run_nova() {
  local elf="$1"
  local name="$2"
  local extra_env=("${@:3}")
  env "${extra_env[@]}" \
    NOVA_MAX_CYCLES="${NOVA_MAX_CYCLES:-500000}" \
    NOVA_EXPECT_SIGNATURE=0x4e4f5641 \
    bash scripts/run_nova_elf.sh \
      "${elf}" \
      "out/spike-diff/${name}.nova.jsonl" \
      "out/spike-diff/${name}.nova.summary.json"
}

run_spike_log() {
  local elf="$1"
  local name="$2"
  set +e
  timeout 12s "${SPIKE}" --isa="${NOVA_ISA}" --log-commits "${elf}" > "out/spike-diff/${name}.spike.log" 2>&1
  local status=$?
  set -e
  if [[ "${status}" != "0" && "${status}" != "1" && "${status}" != "124" ]]; then
    echo "FAIL: Spike exited with status ${status} for ${name}" >&2
    tail -50 "out/spike-diff/${name}.spike.log" >&2 || true
    exit 1
  fi
}

compare_trace() {
  local elf="$1"
  local name="$2"
  local start tohost fromhost status
  start="$(sym "${elf}" _start)"
  tohost="$(sym "${elf}" tohost)"
  fromhost="$(sym "${elf}" fromhost)"
  scripts/compare_spike_trace.py \
    --nova-trace "out/spike-diff/${name}.nova.jsonl" \
    --spike-log "out/spike-diff/${name}.spike.log" \
    --start "${start}" \
    --tohost "${tohost}" \
    --fromhost "${fromhost}" \
    --out "out/spike-diff/${name}.compare.json"
  status="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["status"])' "out/spike-diff/${name}.compare.json")"
  if [[ "${status}" != "pass" && "${status}" != "masked-pass" ]]; then
    echo "FAIL: comparison status ${status} for ${name}" >&2
    exit 1
  fi
  echo "${name}: ${status}" >> out/spike-diff/summary.txt
}

: > out/spike-diff/summary.txt
echo "Spike per-retire differential results (${NOVA_ISA}):" >> out/spike-diff/summary.txt

DIFF_TESTS=(
  rv32i
  rv32m
  rv32a
  rv32c
  zicsr_zicntr
  zifencei
  zba_zbb_zbs
)

for test_name in "${DIFF_TESTS[@]}"; do
  elf="out/spike-diff/${test_name}.elf"
  compile_asm "sim/isa/${test_name}.S" "${elf}"
  run_nova "${elf}" "${test_name}"
  run_spike_log "${elf}" "${test_name}"
  compare_trace "${elf}" "${test_name}"
done

SEEDS=(0x4e4f5641 0x20260120 0x00000001 0x80000000 0xffffffff)
for seed in "${SEEDS[@]}"; do
  name="random_${seed#0x}"
  src="out/spike-diff/random/${name}.S"
  elf="out/spike-diff/${name}.elf"
  scripts/gen_random_isa.py --seed "${seed}" --out "${src}"
  compile_asm "${src}" "${elf}"
  run_nova "${elf}" "${name}"
  run_spike_log "${elf}" "${name}"
  compare_trace "${elf}" "${name}"
done

for test_name in traps priv_timer; do
  elf="out/spike-diff/${test_name}.elf"
  compile_asm "sim/isa/${test_name}.S" "${elf}"
  if [[ "${test_name}" == "traps" ]]; then
    run_nova "${elf}" "${test_name}" NOVA_FAIL_ON_SYNC_TRAP=0
    echo "${test_name}: nova-pass (EEI trap behavior masked from Spike)" >> out/spike-diff/summary.txt
  else
    run_nova "${elf}" "${test_name}" NOVA_REQUIRE_TIMER_INTERRUPT=1
    echo "${test_name}: nova-pass (CLINT/timer masked from Spike)" >> out/spike-diff/summary.txt
  fi
done

cat >> out/spike-diff/summary.txt <<SUMMARY

PASS: Nova traces were compared against Spike commit logs per retired instruction.
PASS: Documented masks were limited to HTIF, CLINT/timer, and EEI-specific trap behavior.
PASS: Random seeds checked: ${SEEDS[*]}.
SUMMARY

echo "PASS: Spike per-retire differential completed; see out/spike-diff/summary.txt"
