# FreeRTOS Port Notes

NovaV1 uses `third_party/FreeRTOS-Kernel` pinned to tag `V11.1.0`.

The intended FreeRTOS execution environment is M-mode only:

- Machine timer interrupt drives the kernel tick.
- CLINT-style `mtime`/`mtimecmp` lives in the external simulator memory map.
- Critical sections manipulate `mstatus.MIE`.
- Context switch code saves/restores integer registers only.
- Floating-point and vector state are absent.

The scaffold under `freertos/` contains the NovaV1 demo configuration, startup code, linker script, and workload source. Full compilation requires the local RISC-V GCC built by `scripts/install_riscv_gcc.sh`.

The demo success criteria are:

- Scheduler starts.
- Timer tick advances.
- Two tasks switch.
- Queue/semaphore path completes.
- Critical section path completes.
- No unexpected trap is observed.
- Pass signature is written to the `nova_pass_signature` RAM symbol.
- The Spike per-retire run exits through the aligned HTIF `tohost` symbol.

`scripts/run_freertos_demo.sh` builds the demo against `FreeRTOS-Kernel` `V11.1.0`, runs the ELF on Spike to the HTIF pass signature, and then runs the same ELF on the NovaV1 ChiselSim ELF runner. The Nova run passes only when `tohost == 1`, `nova_pass_signature == 0x4e4f5641`, a machine timer interrupt is observed, no unexpected synchronous trap occurs, and the scheduler/task/queue/semaphore/critical-section progress counters advance.
