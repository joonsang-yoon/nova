#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"
./mill ${NOVA_MILL_OPTS:-} emitVerilog
