#include <stdint.h>
#include "FreeRTOS.h"
#include "queue.h"
#include "semphr.h"
#include "task.h"

volatile uint32_t nova_pass_signature;
volatile uint32_t nova_scheduler_started;
volatile uint32_t nova_tick_count;
volatile uint32_t nova_producer_count;
volatile uint32_t nova_consumer_count;
volatile uint32_t nova_queue_count;
volatile uint32_t nova_semaphore_count;
volatile uint32_t nova_critical_count;
volatile uint32_t nova_stack_watermark;
volatile uint64_t tohost __attribute__((used, aligned(64), section(".htif")));
volatile uint64_t fromhost __attribute__((used, aligned(64), section(".htif")));

#define NOVA_PASS_SIGNATURE (&nova_pass_signature)

static QueueHandle_t queue;
static SemaphoreHandle_t sem;

static void producer(void *arg) {
  (void)arg;
  uint32_t value = 0x4e4f5641u;
  for (;;) {
    if (xQueueSend(queue, &value, portMAX_DELAY) == pdTRUE) {
      nova_queue_count++;
    }
    if (xSemaphoreGive(sem) == pdTRUE) {
      nova_semaphore_count++;
    }
    nova_producer_count++;
    vTaskDelay(1);
  }
}

static void consumer(void *arg) {
  (void)arg;
  uint32_t value = 0;
  for (;;) {
    if (xSemaphoreTake(sem, portMAX_DELAY) == pdTRUE && xQueueReceive(queue, &value, 0) == pdTRUE) {
      nova_consumer_count++;
      taskENTER_CRITICAL();
      nova_critical_count++;
      nova_stack_watermark = uxTaskGetStackHighWaterMark(NULL);
      if (nova_tick_count != 0 && nova_producer_count != 0 && nova_queue_count != 0 &&
          nova_semaphore_count != 0 && value == 0x4e4f5641u) {
        *NOVA_PASS_SIGNATURE = value;
        tohost = 1;
      }
      taskEXIT_CRITICAL();
      vTaskDelay(1);
    }
  }
}

void main_blinky(void) {
  queue = xQueueCreate(2, sizeof(uint32_t));
  sem = xSemaphoreCreateBinary();
  xTaskCreate(producer, "prod", configMINIMAL_STACK_SIZE, 0, 2, 0);
  xTaskCreate(consumer, "cons", configMINIMAL_STACK_SIZE, 0, 2, 0);
  nova_scheduler_started = 1;
  vTaskStartScheduler();
  *NOVA_PASS_SIGNATURE = 0xdead0001u;
  for (;;) {
  }
}

void vApplicationMallocFailedHook(void) {
  *NOVA_PASS_SIGNATURE = 0xdead0002u;
  for (;;) {
  }
}

void vApplicationStackOverflowHook(TaskHandle_t task, char *name) {
  (void)task;
  (void)name;
  *NOVA_PASS_SIGNATURE = 0xdead0003u;
  for (;;) {
  }
}

void vApplicationTickHook(void) {
  nova_tick_count++;
}
