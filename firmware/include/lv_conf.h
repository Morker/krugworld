/**
 * LVGL settings for Desk Gaby. Only what differs from LVGL's defaults is set
 * here; lv_conf_internal.h fills in the rest.
 */
#if 1 /* enable this file */
#ifndef LV_CONF_H
#define LV_CONF_H

/* RGB565, what the ST7701 panel takes. */
#define LV_COLOR_DEPTH 16

/* LVGL's own heap for widgets and styles. Screen buffers are allocated
   separately (see board.cpp), so this can stay small. Allocated in PSRAM at
   start-up rather than as a static array in internal RAM: HTTPS needs ~45 KB
   of internal RAM for its TLS buffers, and widgets don't need the speed. */
#define LV_MEM_SIZE (96 * 1024U)
#define LV_MEM_POOL_INCLUDE <esp_heap_caps.h>
#define LV_MEM_POOL_ALLOC(size) heap_caps_malloc(size, MALLOC_CAP_SPIRAM)

/* Fonts: small text, numbers, and big headline numbers. */
#define LV_FONT_MONTSERRAT_14 1
#define LV_FONT_MONTSERRAT_20 1
#define LV_FONT_MONTSERRAT_28 1
#define LV_FONT_MONTSERRAT_40 1
#define LV_FONT_DEFAULT &lv_font_montserrat_20

#define LV_USE_LOG 0

#endif /* LV_CONF_H */
#endif
