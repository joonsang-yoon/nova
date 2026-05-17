    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    li x5, 0x123
    csrrw x0, mscratch, x5
    csrrs x6, mscratch, x0
    CHECK_EQ x6, 0x123, 0xbad50001
    li x5, 0x4
    csrrs x6, mscratch, x5
    CHECK_EQ x6, 0x123, 0xbad50002
    csrrs x7, mscratch, x0
    CHECK_EQ x7, 0x127, 0xbad50003
    li x5, 0x20
    csrrc x6, mscratch, x5
    CHECK_EQ x6, 0x127, 0xbad50004
    csrrs x7, mscratch, x0
    CHECK_EQ x7, 0x107, 0xbad50005

    csrrwi x8, mscratch, 5
    CHECK_EQ x8, 0x107, 0xbad50006
    csrrsi x9, mscratch, 8
    CHECK_EQ x9, 5, 0xbad50007
    csrrci x10, mscratch, 1
    CHECK_EQ x10, 13, 0xbad50008
    csrrs x11, mscratch, x0
    CHECK_EQ x11, 12, 0xbad50009

    csrr x0, cycle
    csrr x0, time
    csrr x0, instret
    csrr x0, cycleh
    csrr x0, timeh
    csrr x0, instreth

    PASS
    .option pop
    TEST_DATA
