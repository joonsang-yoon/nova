    .macro TEST_START
    .section .text
    .globl _start
_start:
    .endm

    .macro PASS
    la t0, nova_pass_signature
    li t1, 0x4e4f5641
    sw t1, 0(t0)
    la t0, tohost
    li t1, 1
    sw t1, 0(t0)
.Lpass_loop\@:
    j .Lpass_loop\@
    .endm

    .macro FAIL code=0xbad00001
    la t0, nova_pass_signature
    li t1, \code
    sw t1, 0(t0)
    la t0, tohost
    li t1, 3
    sw t1, 0(t0)
.Lfail_loop\@:
    j .Lfail_loop\@
    .endm

    .macro CHECK_EQ reg, imm, code=0xbad00002
    li t6, \imm
    bne \reg, t6, .Lcheck_fail\@
    j .Lcheck_done\@
.Lcheck_fail\@:
    FAIL \code
.Lcheck_done\@:
    .endm

    .macro CHECK_REG lhs, rhs, code=0xbad00003
    bne \lhs, \rhs, .Lcheck_fail\@
    j .Lcheck_done\@
.Lcheck_fail\@:
    FAIL \code
.Lcheck_done\@:
    .endm

    .macro TEST_DATA
    .section .data
    .balign 4
    .globl nova_pass_signature
nova_pass_signature:
    .word 0
    .balign 64
    .globl tohost
tohost:
    .dword 0
    .globl fromhost
fromhost:
    .dword 0
    .balign 4
scratch_word:
    .word 0x11223344
scratch_word2:
    .word 0
    .endm
