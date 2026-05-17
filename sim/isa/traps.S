    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    la t0, trap_handler
    csrw mtvec, t0
    la t0, scratch_word
    addi t0, t0, 1
misaligned_lw:
    lw t1, 0(t0)
    FAIL 0xbad80001
after_load_trap:
    .word 0
    FAIL 0xbad80002

trap_handler:
    la t0, trap_count
    lw t1, 0(t0)
    addi t1, t1, 1
    sw t1, 0(t0)
    csrr t2, mcause
    li t3, 1
    beq t1, t3, first_trap
    li t3, 2
    beq t1, t3, second_trap
    FAIL 0xbad80003

first_trap:
    li t3, 4
    bne t2, t3, trap_fail
    csrr t4, mepc
    la t5, misaligned_lw
    bne t4, t5, trap_fail
    la t5, after_load_trap
    csrw mepc, t5
    mret

second_trap:
    li t3, 2
    bne t2, t3, trap_fail
    PASS

trap_fail:
    FAIL 0xbad80004

    .option pop
    TEST_DATA
    .section .data
    .balign 4
trap_count:
    .word 0
