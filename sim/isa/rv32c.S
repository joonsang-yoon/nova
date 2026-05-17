    .include "sim/isa/test_macros.S"
    .option push
    .option rvc
    TEST_START

    la sp, scratch_word2
    c.addi16sp sp, 16
    c.addi4spn a0, sp, 16
    c.li a1, 0x12
    c.sw a1, 0(a0)
    c.lw a2, 0(a0)
    CHECK_EQ a2, 0x12, 0xbad40001

    c.nop
    c.li s0, 1
    c.addi s0, 2
    CHECK_EQ s0, 3, 0xbad40002
    c.lui s1, 1
    CHECK_EQ s1, 0x1000, 0xbad40003
    c.srli s0, 1
    CHECK_EQ s0, 1, 0xbad40004
    c.slli s0, 2
    CHECK_EQ s0, 4, 0xbad40005
    c.srai s0, 1
    CHECK_EQ s0, 2, 0xbad40006
    c.andi s0, 3
    CHECK_EQ s0, 2, 0xbad40007

    c.li a3, 5
    c.mv a4, a3
    CHECK_EQ a4, 5, 0xbad40008
    c.add a4, a3
    CHECK_EQ a4, 10, 0xbad40009

    c.li s0, 9
    c.li s1, 2
    c.sub s0, s1
    CHECK_EQ s0, 7, 0xbad4000a
    c.xor s0, s1
    CHECK_EQ s0, 5, 0xbad4000b
    c.or s0, s1
    CHECK_EQ s0, 7, 0xbad4000c
    c.and s0, s1
    CHECK_EQ s0, 2, 0xbad4000d

    c.li s0, 0
    c.beqz s0, 1f
    FAIL 0xbad4000e
1:
    c.li s0, 1
    c.bnez s0, 2f
    FAIL 0xbad4000f
2:
    c.j 3f
    FAIL 0xbad40010
3:
jal_site:
    c.jal jal_target
jal_after:
    FAIL 0xbad40011
jal_target:
    la t0, jal_after
    CHECK_REG ra, t0, 0xbad40012

    la t0, jr_target
    c.jr t0
    FAIL 0xbad40013
jr_target:
    la t0, jalr_target
    c.jalr t0
jalr_after:
    FAIL 0xbad40014
jalr_target:
    la t1, jalr_after
    CHECK_REG ra, t1, 0xbad40015

    c.li a4, 5
    c.swsp a4, 0(sp)
    c.lwsp a5, 0(sp)
    CHECK_EQ a5, 5, 0xbad40016
    PASS
    .option pop
    TEST_DATA
