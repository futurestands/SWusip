#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <jni.h>
#include "logger.h"

int libre_init(void) { return 0; }
void libre_close(void) {}
bool re_thread_check(void) { return true; }
int conf_path_set(const char *path) { (void)path; return 0; }
void log_level_set(int level) { (void)level; }
int log_level_get(void) { return 5; }
int conf_configure(void) { return 0; }
int re_thread_async_init(int workers) { (void)workers; return 0; }
void *conf_config(void) { return NULL; }
int baresip_init(void *cfg) { (void)cfg; return 0; }
void *baresip_network(void) { return NULL; }
void *net_dnsc(void) { return NULL; }
void dnsc_cache_max(void *dnsc, uint32_t max) { (void)dnsc; (void)max; }
int net_flush_addresses(void) { return 0; }
int sa_set_str(void *sa, const char *str, uint16_t port) { (void)sa; (void)str; (void)port; return 0; }
int sa_ntop(const void *sa, char *buf, size_t size) { (void)sa; if(size>0 && buf) buf[0]=0; return 0; }
int net_add_address_ifname(int af, const char *ip, const char *ifname) { (void)af; (void)ip; (void)ifname; return 0; }
int net_rm_address(int af, const char *ip) { (void)af; (void)ip; return 0; }
int ua_init(const char *name, bool reg, bool pub, bool mwi) { (void)name; (void)reg; (void)pub; (void)mwi; return 0; }
void uag_set_exit_handler(void *h, void *arg) { (void)h; (void)arg; }
void bevent_register(void *h, void *arg) { (void)h; (void)arg; }
int baresip_message(void) { return 0; }
void ua_stop_all(bool force) { (void)force; }
void module_app_unload(void) {}
void mod_close(void) {}
