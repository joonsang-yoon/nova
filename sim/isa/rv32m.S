    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    li x1, -2
    li x2, 3
    mul x3, x1, x2
    CHECK_EQ x3, -6, 0xbad20001
    mulh x4, x1, x2
    CHECK_EQ x4, -1, 0xbad20002
    mulhsu x5, x1, x2
    CHECK_EQ x5, -1, 0xbad20003
    mulhu x6, x1, x2
    CHECK_EQ x6, 2, 0xbad20004

    li x7, -7
    div x8, x7, x2
    CHECK_EQ x8, -2, 0xbad20005
    rem x9, x7, x2
    CHECK_EQ x9, -1, 0xbad20006
    divu x10, x7, x2
    CHECK_EQ x10, 0x55555553, 0xbad20007
    remu x11, x7, x2
    CHECK_EQ x11, 0, 0xbad20008

    div x12, x7, x0
    CHECK_EQ x12, -1, 0xbad20009
    divu x13, x7, x0
    CHECK_EQ x13, -1, 0xbad2000a
    rem x14, x7, x0
    CHECK_EQ x14, -7, 0xbad2000b
    remu x15, x7, x0
    CHECK_EQ x15, -7, 0xbad2000c

    li x16, 0x80000000
    li x17, -1
    div x18, x16, x17
    CHECK_EQ x18, 0x80000000, 0xbad2000d
    rem x19, x16, x17
    CHECK_EQ x19, 0, 0xbad2000e

    PASS
    .option pop
    TEST_DATA
