#ifndef TUNNEL_FORGE_PPP_H
#define TUNNEL_FORGE_PPP_H

#include "esp_udp.h"
#include "l2tp.h"
#include "packet_endpoint.h"

#include <stddef.h>
#include <stdint.h>
#include <sys/socket.h>

int ppp_negotiate(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                  l2tp_session_t *l2tp, const char *user, const char *password, int tun_mtu,
                  ppp_session_t *ppp);

int ppp_dispatch_ppp_frame(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                           l2tp_session_t *l2tp, ppp_session_t *ppp, const uint8_t *ppp_ptr, size_t ppp_len,
                           packet_endpoint_t *endpoint);

int ppp_encapsulate_and_send(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                             l2tp_session_t *l2tp, ppp_session_t *ppp, const uint8_t *packet, size_t len);

#endif
