OUTPUT_ARCH(riscv)
ENTRY(_start)

PHDRS
{
  text PT_LOAD FLAGS(5);
  data PT_LOAD FLAGS(6);
}

MEMORY
{
  ram (rwx) : ORIGIN = 0x80000000, LENGTH = 512K
}

SECTIONS
{
  .text : {
    *(.init)
    *(.text*)
    *(.rodata*)
  } > ram :text

  . = ALIGN(16);
  .data : {
    *(.sdata*)
    *(.data*)
  } > ram :data

  PROVIDE(__global_pointer$ = . + 0x800);

  .bss : {
    __bss_start = .;
    *(.sbss*)
    *(.bss*)
    *(COMMON)
    __bss_end = .;
  } > ram :data

  . = ALIGN(16);
  __stack_top = ORIGIN(ram) + LENGTH(ram);
}
