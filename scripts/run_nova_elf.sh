#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_parallelism_cap
cd "${ROOT_DIR}"

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <elf> [trace.jsonl] [summary.json]" >&2
  exit 2
fi

ELF="$(realpath -m "$1")"
TRACE="$(realpath -m "${2:-out/nova-run/trace.jsonl}")"
SUMMARY="$(realpath -m "${3:-out/nova-run/summary.json}")"

mkdir -p "$(dirname "${TRACE}")" "$(dirname "${SUMMARY}")"

NOVA_ELF="${ELF}" \
NOVA_TRACE="${TRACE}" \
NOVA_RUN_SUMMARY="${SUMMARY}" \
./mill ${NOVA_MILL_OPTS:-} test.testOnly nova.v1.NovaV1ElfRunnerSpec
