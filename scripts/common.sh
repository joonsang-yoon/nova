#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export MAKEFLAGS="${MAKEFLAGS:--j4}"
export CMAKE_BUILD_PARALLEL_LEVEL="${CMAKE_BUILD_PARALLEL_LEVEL:-4}"
export NINJAFLAGS="${NINJAFLAGS:--j4}"
export NOVA_ISA="rv32imac_zicsr_zifencei_zicntr_zba_zbb_zbs"
export NOVA_ABI="ilp32"
export NOVA_GCC_PREFIX="${ROOT_DIR}/tools/riscv"
export NOVA_SPIKE_PREFIX="${ROOT_DIR}/tools/spike"

require_parallelism_cap() {
  case "${MAKEFLAGS} ${CMAKE_BUILD_PARALLEL_LEVEL} ${NINJAFLAGS}" in
    *-j5*|*-j6*|*-j7*|*-j8*|*-j9*|*"PARALLEL_LEVEL=5"*|*"PARALLEL_LEVEL=6"*|*"PARALLEL_LEVEL=7"*|*"PARALLEL_LEVEL=8"*|*"PARALLEL_LEVEL=9"*)
      echo "Parallelism exceeds the NovaV1 cap of 4" >&2
      exit 1
      ;;
  esac
}
