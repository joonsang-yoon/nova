    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    la x5, scratch_word
    li x6, 5
    lr.w x7, (x5)
    CHECK_EQ x7, 0x11223344, 0xbad30001
    sc.w x8, x6, (x5)
    CHECK_EQ x8, 0, 0xbad30002
    lw x9, 0(x5)
    CHECK_EQ x9, 5, 0xbad30003

    sw x0, 0(x5)
    sc.w x8, x6, (x5)
    CHECK_EQ x8, 1, 0xbad30004
    lw x9, 0(x5)
    CHECK_EQ x9, 0, 0xbad30005

    la x5, scratch_word2
    li x6, 7
    sw x6, 0(x5)
    li x6, 9
    amoswap.w.aq x10, x6, (x5)
    CHECK_EQ x10, 7, 0xbad30006
    li x6, 3
    amoadd.w.rl x10, x6, (x5)
    CHECK_EQ x10, 9, 0xbad30007
    li x6, 0xf
    amoxor.w x10, x6, (x5)
    CHECK_EQ x10, 12, 0xbad30008
    li x6, 6
    amoand.w x10, x6, (x5)
    CHECK_EQ x10, 3, 0xbad30009
    li x6, 8
    amoor.w x10, x6, (x5)
    CHECK_EQ x10, 2, 0xbad3000a
    li x6, -1
    amomin.w x10, x6, (x5)
    CHECK_EQ x10, 10, 0xbad3000b
    li x6, 5
    amomax.w x10, x6, (x5)
    CHECK_EQ x10, -1, 0xbad3000c
    li x6, -2
    amominu.w x10, x6, (x5)
    CHECK_EQ x10, 5, 0xbad3000d
    li x6, -2
    amomaxu.w x10, x6, (x5)
    CHECK_EQ x10, 5, 0xbad3000e
    lw x11, 0(x5)
    CHECK_EQ x11, -2, 0xbad3000f

    PASS
    .option pop
    TEST_DATA
