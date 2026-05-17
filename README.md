# NovaV1

NovaV1 is a Mill-only Chisel repository for a single-hart RV32 microcontroller-class architectural model. It is not ARM compatible. It targets Cortex-M3-class embedded software/platform workloads using the RISC-V ISA string:

```text
rv32imac_zicsr_zifencei_zicntr_zba_zbb_zbs
```

The hart reports:

```text
misa = 0x40001107
```

The local ISA source documents are `riscv-unprivileged.pdf` and `riscv-privileged.pdf`, both Version `20260120`.

## Build

```bash
./mill __.compile
./mill __.test
./mill emitVerilog
```

## Verification Commands

```bash
./scripts/run_isa_tests.sh
./scripts/run_spike_diff.sh
./scripts/run_freertos_demo.sh
./scripts/run_regression.sh
```

The Spike and FreeRTOS commands become full external-tool runs after building the local toolchain:

```bash
./scripts/install_riscv_gcc.sh
./scripts/install_spike.sh
```

`./scripts/run_regression.sh` now fails if the local GCC/Spike-backed stages are unavailable or fail. The current Spike flow is a per-retire differential, and the FreeRTOS flow builds the NovaV1 demo and runs it on Spike and on the Nova ChiselSim ELF runner to an HTIF pass signature; see `docs/VERIFICATION_REPORT.md` for the exact evidence boundary.

All repository scripts cap parallelism at 4 through `MAKEFLAGS=-j4`, `CMAKE_BUILD_PARALLEL_LEVEL=4`, and static checks.

## IO Contract

`NovaV1` is constructed with `NovaV1Params` and exposes a SoC-oriented IP boundary:

- `mem.instruction`: read-only AXI4 instruction master, 32-bit address/data, single outstanding, single-beat reads.
- `mem.data`: AXI4 data master, 32-bit address/data, single outstanding, single-beat reads/writes.
- `reg.control`: AXI4-Lite control slave with interface version, boot PC, run/halt, hart ID, ISA, status, and trap registers.
- `platform`: direct `mtime` plus `irq.machineSoftware`, `irq.machineTimer`, and `irq.machineExternal` platform inputs.
- `debug.hart`: direct run/halt/trap/current-PC/trace-backpressure debug status outputs.
- `debug.retire`: always-present `Decoupled[NovaV1RetireTrace]` retirement stream. When `NovaV1Params.enableTrace` is false, `debug.retire.valid` is tied low and `debug.retire.bits` are zero.

Instruction fetches read aligned 32-bit AXI4 words and assemble RV32C halfword and halfword-crossing 32-bit instructions internally. Data accesses use AXI4 byte strobes and AXI4 exclusive transactions for LR/SC and AMO read-modify-write sequences. `debug.retire` has a one-entry skid buffer: retirement and interrupt trace events are not dropped, and backpressure is reported through `debug.hart.traceBackpressured`.

The AXI4 master ports drive fixed single-beat metadata: `len = 0`, `burst = INCR`, `qos = 0`, and `region = 0`. `mem.instruction` uses instruction `prot`; `mem.data` uses data `prot`. `NovaV1Params` provides `axiIdWidth`, `instructionAxiId`, and `dataAxiId`.

`reg.control` register map:

| Offset | Name | Access | Description |
|---:|---|---|---|
| `0x000` | `IP_ID` | RO | ASCII `"NOVA"` |
| `0x004` | `IP_VERSION` | RO | Implementation version |
| `0x008` | `INTERFACE_VERSION` | RO | SoC IP interface version, currently `1` |
| `0x00c` | `CAPABILITIES` | RO | Trace, separate I/D, AXI exclusive, and ISA feature bits |
| `0x010` | `HART_ID` | RO | Configured hart ID |
| `0x014` | `MISA` | RO | `0x40001107` |
| `0x020` | `CORE_STATUS` | RO | Bit 0 running, 1 halted, 2 trap seen, 3 interrupt pending |
| `0x024` | `TRACE_STATUS` | RO | Bit 0 trace present, 1 trace stalled |
| `0x028` | `CURRENT_PC` | RO | Current hart PC |
| `0x02c` | `LAST_TRAP_CAUSE` | RO | Last `mcause` value |
| `0x030` | `LAST_TRAP_VALUE` | RO | Last `mtval` value |
| `0x040` | `COMMAND` | WO | Bit 0 soft reset, 1 halt, 2 resume, 3 set PC, 4 clear trap |
| `0x044` | `BOOT_PC` | RW | Byte-strobed boot PC/set-PC target |
