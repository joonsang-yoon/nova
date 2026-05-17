# NovaV1 Execution Latency Notes

NovaV1 uses this definition:

> Once an instruction word and any required LSU operand/result are available inside the core, NovaV1 performs architectural decode, execute, CSR/trap handling, and retire in one core edge. AXI4 fetch, AXI4 data, AXI4-Lite control, exclusive retry, interrupt-entry, and retire-trace backpressure waits are external or front-end/LSU/debug wait states rather than ALU/control execution latency.

## Design Argument

- `NovaV1` has no pipeline registers, caches, TLBs, branch predictors, prefetch queues, scoreboards, reorder buffers, microcode state, or iterative multiply/divide state.
- Instruction decode and non-memory execute are combinational in `NovaV1.scala`.
- Instruction fetch is an aligned-word AXI4 FSM that also assembles compressed and halfword-crossing instructions.
- The LSU is a single-outstanding AXI4 FSM for loads, stores, LR/SC, and AMO exclusive read-modify-write loops.
- Architectural registers, PC, CSRs, counters, and hart status state update only in the sequential block that completes control, interrupt, execute, or LSU events.
- Multiply, divide, remainder, bitmanip, CSR, compressed decode, branch, jump, FENCE, and FENCE.I feed the same single retire edge after fetch completes.
- Retire trace uses a one-entry Decoupled skid buffer. If the buffer is full and the trace consumer is not ready, the hart stalls before the next trace-emitting architectural update.

## Assertions

The Chisel module asserts:

- Retired trace events are not trap events.
- x0 writes are suppressed.

The directed execution suite `nova.v1.NovaV1ExecutionSpec` adds AXI-backed end-to-end checks:

- RV32I/M/Zba/Zbb/Zbs/CSR instruction streams retire with correct architectural trace.
- Load/store and LR/SC/AMO tests run through AXI read/write/exclusive channels.
- Compressed and halfword-crossing 32-bit instructions are fetched through aligned AXI reads.
- AXI4-Lite control registers set PC, resume, halt, clear trap status, and report debug hart state.

## Environmental Waits

AXI4 backpressure, memory latency, instruction fetch sequencing, data response latency, AMO exclusive retries, and Decoupled retire-trace backpressure stall the hart. These waits are modeled explicitly and are not counted as ALU/control execution latency.
