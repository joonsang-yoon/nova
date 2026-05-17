    .section .text
    .globl _start
_start:
    li x1, 5
    li x2, 10
    add x3, x1, x2
    li x4, 15
    bne x3, x4, fail

    li x5, -1
    div x6, x5, x0
    li x7, -1
    bne x6, x7, fail

    li x10, 0x1234
    rev8 x11, x10
    li x12, 0x34120000
    bne x11, x12, fail

    li x13, 5
    bseti x14, x13, 4
    li x15, 21
    bne x14, x15, fail

pass:
    la t0, tohost
    li t1, 1
    sw t1, 0(t0)
1:
    j 1b

fail:
    la t0, tohost
    li t1, 3
    sw t1, 0(t0)
1:
    j 1b

    .align 6
    .globl tohost
tohost:
    .dword 0
    .globl fromhost
fromhost:
    .dword 0
