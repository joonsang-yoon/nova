    .include "sim/isa/test_macros.S"
    .option push
    .option norvc
    TEST_START

    li x5, 1
    fence.i
    addi x5, x5, 1
    CHECK_EQ x5, 2, 0xbad60001

    PASS
    .option pop
    TEST_DATA
