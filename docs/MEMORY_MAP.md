# NovaV1 Memory Map

NovaV1 is little-endian and has no cache, TLB, or virtual memory.

## Simulator Map

| Region | Address Range | Purpose |
|---|---:|---|
| ROM/RAM test image | `0x0000_0000` upward | Instruction and data image used by directed ChiselSim tests |
| FreeRTOS RAM image | `0x8000_0000` upward | FreeRTOS/Spike ELF load region |
| CLINT-style timer | `0x0200_0000` - `0x0200_FFFF` | External simulator device for `mtime`, `mtimecmp`, and timer interrupt |
| Spike HTIF | ELF symbols `tohost`/`fromhost` | Spike per-retire pass/fail exit for directed and FreeRTOS runs |
| Pass/fail signature | RAM symbol `nova_pass_signature` | FreeRTOS workload completion signature |

## Memory Semantics

- Instruction fetch uses aligned 32-bit AXI4 reads on `mem.instruction`. NovaV1 assembles RV32C halfword instructions and halfword-crossing 32-bit instructions internally.
- Data loads use AXI4 reads on `mem.data` with byte/half/word size metadata and extract the requested little-endian lane internally.
- Stores provide AXI4 write address size, shifted write data, and byte strobes.
- LR/SC use AXI4 exclusive read/write transactions. SC writes `0` to `rd` on `EXOKAY` and `1` otherwise.
- AMOs use internal exclusive read-modify-write loops and retire with the old memory word in `rd`.
- Naturally aligned loads/stores do not raise address-misaligned exceptions.
- Non-natural loads/stores raise address-misaligned exceptions.
- LR/SC/AMO word operations require 4-byte alignment and trap on misalignment.

## Control Map

The `reg.control` AXI4-Lite slave exposes the long-lived SoC IP control plane.

| Offset | Name | Access | Description |
|---:|---|---|---|
| `0x000` | `IP_ID` | RO | ASCII `"NOVA"` |
| `0x004` | `IP_VERSION` | RO | Implementation version |
| `0x008` | `INTERFACE_VERSION` | RO | SoC IP interface version, currently `1` |
| `0x00c` | `CAPABILITIES` | RO | Bit 0 trace present, 1 separate I/D, 2 AXI exclusive, 3 RV32IMAC, 4 Zicsr, 5 Zifencei, 6 Zicntr, 7 Zba, 8 Zbb, 9 Zbs |
| `0x010` | `HART_ID` | RO | Configured hart ID |
| `0x014` | `MISA` | RO | `0x40001107` |
| `0x020` | `CORE_STATUS` | RO | Bit 0 running, 1 halted, 2 trap seen, 3 interrupt pending |
| `0x024` | `TRACE_STATUS` | RO | Bit 0 trace present, 1 trace stalled |
| `0x028` | `CURRENT_PC` | RO | Current hart PC |
| `0x02c` | `LAST_TRAP_CAUSE` | RO | Last `mcause` value |
| `0x030` | `LAST_TRAP_VALUE` | RO | Last `mtval` value |
| `0x040` | `COMMAND` | WO | Bit 0 soft reset, 1 halt, 2 resume, 3 set PC, 4 clear trap |
| `0x044` | `BOOT_PC` | RW | Byte-strobed boot PC/set-PC target |

Invalid `reg.control` reads and writes return AXI4-Lite `SLVERR`.
