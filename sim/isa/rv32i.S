    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    lui x1, 0x12345
    addi x1, x1, 0x678
    CHECK_EQ x1, 0x12345678, 0xbad10001

    auipc x2, 0
    addi x2, x2, 8
    jal x3, 1f
    FAIL 0xbad10002
1:
    addi x4, x3, -4
    CHECK_REG x2, x4, 0xbad10003

    la x5, 2f
    addi x5, x5, 1
    jalr x6, 0(x5)
    FAIL 0xbad10004
2:
    andi x7, x6, 1
    CHECK_EQ x7, 0, 0xbad10005

    li x8, -3
    li x9, 2
    blt x8, x9, 3f
    FAIL 0xbad10006
3:
    bge x9, x8, 4f
    FAIL 0xbad10007
4:
    bltu x9, x8, 5f
    FAIL 0xbad10008
5:
    bgeu x8, x9, 6f
    FAIL 0xbad10009
6:
    beq x8, x8, 7f
    FAIL 0xbad1000a
7:
    bne x8, x9, 8f
    FAIL 0xbad1000b
8:
    li x10, 0x55
    la x11, scratch_word2
    sb x10, 0(x11)
    lbu x12, 0(x11)
    CHECK_EQ x12, 0x55, 0xbad1000c
    li x10, -1
    sb x10, 1(x11)
    lb x12, 1(x11)
    CHECK_EQ x12, -1, 0xbad1000d
    li x10, 0x7abc
    sh x10, 2(x11)
    lhu x12, 2(x11)
    CHECK_EQ x12, 0x7abc, 0xbad1000e
    li x10, -2
    sh x10, 0(x11)
    lh x12, 0(x11)
    CHECK_EQ x12, -2, 0xbad1000f
    li x10, 0x76543210
    sw x10, 0(x11)
    lw x12, 0(x11)
    CHECK_EQ x12, 0x76543210, 0xbad10010

    addi x13, x0, -4
    slti x14, x13, -3
    CHECK_EQ x14, 1, 0xbad10011
    sltiu x14, x13, 1
    CHECK_EQ x14, 0, 0xbad10012
    xori x14, x13, -1
    CHECK_EQ x14, 3, 0xbad10013
    ori x14, x0, 0x123
    CHECK_EQ x14, 0x123, 0xbad10014
    andi x14, x14, 0x23
    CHECK_EQ x14, 0x23, 0xbad10015
    slli x14, x14, 3
    CHECK_EQ x14, 0x118, 0xbad10016
    srli x14, x14, 4
    CHECK_EQ x14, 0x11, 0xbad10017
    li x15, -8
    srai x14, x15, 1
    CHECK_EQ x14, -4, 0xbad10018

    add x16, x14, x9
    CHECK_EQ x16, -2, 0xbad10019
    sub x16, x9, x14
    CHECK_EQ x16, 6, 0xbad1001a
    sll x16, x9, x9
    CHECK_EQ x16, 8, 0xbad1001b
    slt x16, x14, x9
    CHECK_EQ x16, 1, 0xbad1001c
    sltu x16, x14, x9
    CHECK_EQ x16, 0, 0xbad1001d
    xor x16, x9, x13
    CHECK_EQ x16, -2, 0xbad1001e
    srl x16, x13, x9
    CHECK_EQ x16, 0x3fffffff, 0xbad1001f
    sra x16, x13, x9
    CHECK_EQ x16, -1, 0xbad10020
    or x16, x9, x13
    CHECK_EQ x16, -2, 0xbad10021
    and x16, x9, x13
    CHECK_EQ x16, 0, 0xbad10022

    fence
    fence.i
    PASS
    .option pop
    TEST_DATA
