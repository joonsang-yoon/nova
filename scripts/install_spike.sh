#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"

SRC="${ROOT_DIR}/tools/src/riscv-isa-sim"
mkdir -p "${ROOT_DIR}/tools/src" "${NOVA_SPIKE_PREFIX}"
if [[ ! -d "${SRC}/.git" ]]; then
  git clone https://github.com/riscv-software-src/riscv-isa-sim "${SRC}"
fi

mkdir -p "${SRC}/build"
cd "${SRC}/build"
../configure --prefix="${NOVA_SPIKE_PREFIX}"
make
make install
