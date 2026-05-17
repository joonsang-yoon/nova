#pragma once

#define configUSE_PREEMPTION 1
#define configUSE_PORT_OPTIMISED_TASK_SELECTION 0
#define configCPU_CLOCK_HZ 1000000UL
#define configTICK_RATE_HZ 1000
#define configMTIME_BASE_ADDRESS 0x0200BFF8UL
#define configMTIMECMP_BASE_ADDRESS 0x02004000UL
#define configMAX_PRIORITIES 5
#define configMINIMAL_STACK_SIZE 128
#define configTOTAL_HEAP_SIZE (16 * 1024)
#define configMAX_TASK_NAME_LEN 16
#define configUSE_16_BIT_TICKS 0
#define configIDLE_SHOULD_YIELD 1
#define configUSE_MUTEXES 1
#define configUSE_COUNTING_SEMAPHORES 1
#define configUSE_RECURSIVE_MUTEXES 0
#define configUSE_MALLOC_FAILED_HOOK 1
#define configCHECK_FOR_STACK_OVERFLOW 2
#define configUSE_TICK_HOOK 1
#define configUSE_IDLE_HOOK 0
#define configUSE_TIMERS 0
#define configISR_STACK_SIZE_WORDS 256

#define configKERNEL_INTERRUPT_PRIORITY 0
#define configMAX_SYSCALL_INTERRUPT_PRIORITY 0

#define INCLUDE_vTaskDelay 1
#define INCLUDE_vTaskDelete 1
#define INCLUDE_xTaskGetSchedulerState 1
#define INCLUDE_uxTaskGetStackHighWaterMark 1
