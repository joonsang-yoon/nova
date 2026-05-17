# Toolchain Install Log

## Local Environment

- Repository: `/home/joonsang/nova`
- Java present before setup: OpenJDK `17.0.14`
- Mill bootstrap: `./mill` downloads `mill-dist-1.1.5.exe` from Maven Central
- Mill runtime observed: Mill `1.1.5`, Azul JDK `21.0.10` via coursier
- Parallelism cap: `MAKEFLAGS=-j4`, `CMAKE_BUILD_PARALLEL_LEVEL=4`, `NINJAFLAGS=-j4`

## Mill/Chisel

Configured in active Mill `build.mill`:

```text
Mill: 1.1.5
Scala: 2.13.16
Chisel: 7.11.0
firtool-resolver: 7.11.0
ScalaTest: 3.2.20
```

Commands run:

```bash
./mill --version
./mill __.compile
./mill __.test
./mill emitVerilog
```

## RISC-V GCC

Source:

```text
https://github.com/riscv-collab/riscv-gnu-toolchain
superproject commit: 21f630d1c8bad71aa4805c970bba93a7d53e7096
```

Install command:

```bash
./scripts/install_riscv_gcc.sh
```

Expanded commands:

```bash
export MAKEFLAGS=-j4
export CMAKE_BUILD_PARALLEL_LEVEL=4
export NOVA_ISA=rv32imac_zicsr_zifencei_zicntr_zba_zbb_zbs
export NOVA_ABI=ilp32
git clone --recursive https://github.com/riscv-collab/riscv-gnu-toolchain tools/src/riscv-gnu-toolchain
cd tools/src/riscv-gnu-toolchain
./configure --prefix=/home/joonsang/nova/tools/riscv --with-arch=rv32imac_zicsr_zifencei_zicntr_zba_zbb_zbs --with-abi=ilp32
make newlib
```

Observed compiler:

```text
riscv32-unknown-elf-gcc (g5115c7e447) 15.2.0
```

Observed submodule commits include:

```text
binutils 49d4d3fafa4ec4ff5a3460d91d5b1ed5286487db
gcc      5115c7e447fc07457443df874bf57840e8316d5f
gdb      631a49c452a4a456dd9889d172541ea789f8bcae
glibc    d2097651cc57834dbfcaa102ddfacae0d86cfb66
llvm     5a86dc996c26299de63effc927075dcbfb924167
newlib   8ba4275b83ec27529f67e0d477611fa6d8d6e6bd
spike    8fc5ab0357a7cc9a19f79c93b0370103cfe3ce84
```

Use `-mcmodel=medany` for NovaV1 bare-metal and FreeRTOS programs.

## Spike

Source:

```text
https://github.com/riscv-software-src/riscv-isa-sim
commit: b14b59ed5699d6319f3ecd16f836f1dcb564644b
```

Install command:

```bash
./scripts/install_spike.sh
```

Expanded commands:

```bash
export MAKEFLAGS=-j4
export CMAKE_BUILD_PARALLEL_LEVEL=4
git clone https://github.com/riscv-software-src/riscv-isa-sim tools/src/riscv-isa-sim
mkdir -p tools/src/riscv-isa-sim/build
cd tools/src/riscv-isa-sim/build
../configure --prefix=/home/joonsang/nova/tools/spike
make
make install
tools/spike/bin/spike --isa=rv32imac_zicsr_zifencei_zicntr_zba_zbb_zbs <elf>
```

## FreeRTOS

Source:

```text
https://github.com/FreeRTOS/FreeRTOS-Kernel
```

Commands run:

```bash
git submodule add https://github.com/FreeRTOS/FreeRTOS-Kernel third_party/FreeRTOS-Kernel
git -C third_party/FreeRTOS-Kernel checkout V11.1.0
git -C third_party/FreeRTOS-Kernel rev-parse HEAD
```

Observed pinned commit:

```text
dbf70559b27d39c1fdb68dfb9a32140b6a6777a0
```
