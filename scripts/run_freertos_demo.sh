#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"

GCC="${NOVA_GCC_PREFIX}/bin/riscv32-unknown-elf-gcc"
SPIKE="${NOVA_SPIKE_PREFIX}/bin/spike"
if [[ ! -x "${GCC}" ]]; then
  echo "FAIL: local RISC-V GCC is not built." >&2
  echo "Run scripts/install_riscv_gcc.sh first." >&2
  exit 1
fi
if [[ ! -x "${SPIKE}" ]]; then
  echo "FAIL: local Spike is not built; the FreeRTOS reference run uses Spike HTIF for pass/fail." >&2
  echo "Run scripts/install_spike.sh first." >&2
  exit 1
fi

mkdir -p out/freertos

COMMON_FLAGS=(
  -march="${NOVA_ISA}"
  -mabi="${NOVA_ABI}"
  -mcmodel=medany
  -ffreestanding
  -fno-builtin
  -ffunction-sections
  -fdata-sections
  -Os
  -g
  -Wall
  -Wextra
  -Ifreertos
  -Ithird_party/FreeRTOS-Kernel/include
  -Ithird_party/FreeRTOS-Kernel/portable/GCC/RISC-V
  -Ithird_party/FreeRTOS-Kernel/portable/GCC/RISC-V/chip_specific_extensions/RISCV_MTIME_CLINT_no_extensions
)

SOURCES=(
  freertos/startup.S
  freertos/nova_v1_demo.c
  third_party/FreeRTOS-Kernel/tasks.c
  third_party/FreeRTOS-Kernel/queue.c
  third_party/FreeRTOS-Kernel/list.c
  third_party/FreeRTOS-Kernel/timers.c
  third_party/FreeRTOS-Kernel/event_groups.c
  third_party/FreeRTOS-Kernel/stream_buffer.c
  third_party/FreeRTOS-Kernel/croutine.c
  third_party/FreeRTOS-Kernel/portable/MemMang/heap_4.c
  third_party/FreeRTOS-Kernel/portable/GCC/RISC-V/port.c
  third_party/FreeRTOS-Kernel/portable/GCC/RISC-V/portASM.S
)

"${GCC}" "${COMMON_FLAGS[@]}" \
  -nostartfiles \
  -Wl,--gc-sections \
  -Wl,-Map=out/freertos/nova_v1_freertos.map \
  -T freertos/linker.ld \
  -o out/freertos/nova_v1_freertos.elf \
  "${SOURCES[@]}"

timeout 60s "${SPIKE}" \
  --isa="${NOVA_ISA}" \
  out/freertos/nova_v1_freertos.elf \
  > out/freertos/spike_freertos.log 2>&1

NOVA_MAX_CYCLES="${NOVA_FREERTOS_MAX_CYCLES:-200000}" \
NOVA_EXPECT_SIGNATURE=0x4e4f5641 \
NOVA_REQUIRE_TIMER_INTERRUPT=1 \
NOVA_REQUIRE_FREERTOS_PROGRESS=1 \
bash scripts/run_nova_elf.sh \
  out/freertos/nova_v1_freertos.elf \
  out/freertos/nova_freertos.jsonl \
  out/freertos/nova_freertos.summary.json \
  > out/freertos/nova_freertos.log 2>&1

cat > out/freertos/summary.txt <<SUMMARY
PASS: FreeRTOS-Kernel V11.1.0 NovaV1 demo built with local RISC-V GCC.
PASS: Spike executed the FreeRTOS ELF to the HTIF pass signature written by the consumer task.
PASS: NovaV1 ChiselSim ELF runner executed the same ELF to the HTIF pass signature.

Required Nova evidence:
- tohost == 1
- nova_pass_signature == 0x4e4f5641
- machine timer interrupt observed
- scheduler/task/queue/semaphore/critical-section progress counters advanced

Covered by the demo source: scheduler start, two tasks, queue, binary semaphore, task delay, critical section entry/exit, and CLINT mtime/mtimecmp timer setup.
SUMMARY

cat out/freertos/nova_freertos.summary.json >> out/freertos/summary.txt

echo "PASS: FreeRTOS build, Spike reference run, and Nova ChiselSim run completed; see out/freertos"
