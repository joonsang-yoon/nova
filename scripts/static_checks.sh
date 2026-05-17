#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"

if find . -name '*.sbt' -print -quit | grep -q .; then
  echo "FAIL: sbt file found" >&2
  exit 1
fi

if find . -path './project/*' -print -quit | grep -q .; then
  echo "FAIL: sbt project metadata found" >&2
  exit 1
fi

if rg -- '-march=.*rv32[^ "]*[fdqv]' scripts freertos src docs README.md >/dev/null; then
  echo "FAIL: unsupported ISA extension in compile flags" >&2
  exit 1
fi

if rg --glob '!scripts/common.sh' -- '-j([5-9]|[1-9][0-9]+)|PARALLEL_LEVEL=([5-9]|[1-9][0-9]+)' scripts docs README.md >/dev/null; then
  echo "FAIL: repository command exceeds parallelism cap of 4" >&2
  exit 1
fi

if ! rg 'MisaValue\s*=\s*"h40001107"' src/main/scala/nova/v1 >/dev/null; then
  echo "FAIL: misa constant is not 0x40001107" >&2
  exit 1
fi

if [[ ! -x tools/riscv/bin/riscv32-unknown-elf-gcc ]]; then
  echo "FAIL: local RISC-V GCC is missing" >&2
  exit 1
fi

if [[ ! -x tools/spike/bin/spike ]]; then
  echo "FAIL: local Spike is missing" >&2
  exit 1
fi

if [[ -e third_party/FreeRTOS-Kernel/.git ]]; then
  tag="$(git -C third_party/FreeRTOS-Kernel describe --tags --exact-match 2>/dev/null || true)"
  if [[ "${tag}" != "V11.1.0" ]]; then
    echo "FAIL: FreeRTOS-Kernel is not pinned at V11.1.0" >&2
    exit 1
  fi
fi

if rg 'TODO|TBD|MISSING|script-ready|limited-smoke-pass' docs/ISA_COVERAGE_MATRIX.md >/dev/null 2>&1; then
  echo "FAIL: coverage matrix contains incomplete markers" >&2
  exit 1
fi

if [[ -f docs/VERIFICATION_REPORT.md ]] && rg -i 'SKIPPED|script-ready|limited-smoke-pass|smoke|not yet|residual limitation|remaining gap|not been run' docs/VERIFICATION_REPORT.md >/dev/null 2>&1; then
  echo "FAIL: verification report contains skipped or stale evidence markers" >&2
  exit 1
fi

if [[ ! -f out/spike-diff/summary.txt ]] || ! rg 'PASS: Nova traces were compared against Spike commit logs per retired instruction' out/spike-diff/summary.txt >/dev/null; then
  echo "FAIL: missing Spike per-retire evidence artifact" >&2
  exit 1
fi

if [[ ! -f out/freertos/nova_freertos.summary.json ]] || ! rg '"passed": true' out/freertos/nova_freertos.summary.json >/dev/null; then
  echo "FAIL: missing passing Nova FreeRTOS evidence artifact" >&2
  exit 1
fi

echo "PASS: static checks"
