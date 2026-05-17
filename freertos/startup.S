    .section .init, "ax"
    .globl _start
_start:
    la sp, __stack_top
    .option push
    .option norelax
    la gp, __global_pointer$
    .option pop

    la t0, __bss_start
    la t1, __bss_end
1:
    bgeu t0, t1, 2f
    sw zero, 0(t0)
    addi t0, t0, 4
    j 1b

2:
    la t0, freertos_risc_v_trap_handler
    csrw mtvec, t0
    csrw mie, zero
    csrw mstatus, zero
    call main_blinky

3:
    wfi
    j 3b
