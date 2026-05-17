    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    li x1, 3
    li x2, 4
    sh1add x3, x1, x2
    CHECK_EQ x3, 10, 0xbad70001
    sh2add x3, x1, x2
    CHECK_EQ x3, 16, 0xbad70002
    sh3add x3, x1, x2
    CHECK_EQ x3, 28, 0xbad70003

    li x4, 10
    li x5, 12
    andn x6, x4, x5
    CHECK_EQ x6, 2, 0xbad70004
    orn x6, x4, x5
    CHECK_EQ x6, -5, 0xbad70005
    xnor x6, x4, x5
    CHECK_EQ x6, -7, 0xbad70006

    li x7, 0x10
    clz x8, x7
    CHECK_EQ x8, 27, 0xbad70007
    ctz x8, x7
    CHECK_EQ x8, 4, 0xbad70008
    li x7, 0xf0f0
    cpop x8, x7
    CHECK_EQ x8, 8, 0xbad70009

    li x9, -1
    li x10, 1
    min x11, x9, x10
    CHECK_EQ x11, -1, 0xbad7000a
    minu x11, x9, x10
    CHECK_EQ x11, 1, 0xbad7000b
    max x11, x9, x10
    CHECK_EQ x11, 1, 0xbad7000c
    maxu x11, x9, x10
    CHECK_EQ x11, -1, 0xbad7000d

    li x12, 0x80
    sext.b x13, x12
    CHECK_EQ x13, -128, 0xbad7000e
    li x12, 0x8001
    sext.h x13, x12
    CHECK_EQ x13, 0xffff8001, 0xbad7000f
    zext.h x13, x12
    CHECK_EQ x13, 0x8001, 0xbad70010

    li x14, 0x80000001
    li x15, 1
    rol x16, x14, x15
    CHECK_EQ x16, 3, 0xbad70011
    ror x16, x14, x15
    CHECK_EQ x16, 0xc0000000, 0xbad70012
    rori x16, x14, 4
    CHECK_EQ x16, 0x18000000, 0xbad70013

    li x17, 0x00120000
    orc.b x18, x17
    CHECK_EQ x18, 0x00ff0000, 0xbad70014
    li x17, 0x12345678
    rev8 x18, x17
    CHECK_EQ x18, 0x78563412, 0xbad70015

    li x19, 0x10
    li x20, 1
    bset x21, x19, x20
    CHECK_EQ x21, 0x12, 0xbad70016
    bclr x21, x21, x20
    CHECK_EQ x21, 0x10, 0xbad70017
    binv x21, x21, x20
    CHECK_EQ x21, 0x12, 0xbad70018
    bext x22, x21, x20
    CHECK_EQ x22, 1, 0xbad70019
    bseti x21, x19, 3
    CHECK_EQ x21, 0x18, 0xbad7001a
    bclri x21, x21, 3
    CHECK_EQ x21, 0x10, 0xbad7001b
    binvi x21, x21, 0
    CHECK_EQ x21, 0x11, 0xbad7001c
    bexti x22, x21, 4
    CHECK_EQ x22, 1, 0xbad7001d

    PASS
    .option pop
    TEST_DATA
