#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"

SRC="${ROOT_DIR}/tools/src/riscv-gnu-toolchain"
mkdir -p "${ROOT_DIR}/tools/src" "${NOVA_GCC_PREFIX}"
if [[ ! -d "${SRC}/.git" ]]; then
  git clone --recursive https://github.com/riscv-collab/riscv-gnu-toolchain "${SRC}"
else
  git -C "${SRC}" submodule update --init --recursive
fi

cd "${SRC}"
./configure --prefix="${NOVA_GCC_PREFIX}" --with-arch="${NOVA_ISA}" --with-abi="${NOVA_ABI}"
make newlib
