    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    la t0, timer_handler
    csrw mtvec, t0

    li t0, 0x0200bff8
    lw t1, 0(t0)
    lw t2, 4(t0)
    addi t1, t1, 32
    sltiu t3, t1, 32
    add t2, t2, t3

    li t0, 0x02004000
    li t4, -1
    sw t4, 4(t0)
    sw t1, 0(t0)
    sw t2, 4(t0)

    li t0, 0x80
    csrw mie, t0
    li t0, 0x8
    csrs mstatus, t0
1:
    wfi
    j 1b

timer_handler:
    csrr t0, mcause
    li t1, 0x80000007
    bne t0, t1, timer_fail
    li t0, 0
    csrw mie, t0
    PASS

timer_fail:
    FAIL 0xbad90001

    .option pop
    TEST_DATA
