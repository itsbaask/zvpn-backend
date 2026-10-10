#ifndef TUNNEL_FORGE_L2TP_H
#define TUNNEL_FORGE_L2TP_H

#include "esp_udp.h"
#include "packet_endpoint.h"

#include <stddef.h>
#include <stdint.h>
#include <sys/socket.h>

typedef struct {
  uint16_t tunnel_id;
  uint16_t session_id;
  uint16_t peer_tunnel_id;
  uint16_t peer_session_id;
  uint16_t send_ns;
  uint16_t recv_nr_expected;
} l2tp_session_t;

typedef struct {
  uint8_t local_ip[4];
  uint8_t peer_ip[4];
  uint8_t primary_dns[4];
  uint8_t secondary_dns[4];
  int have_ip;
  int have_primary_dns;
  int have_secondary_dns;
  uint16_t link_mtu;
  uint16_t tcp_mss;
  int lcp_acfc;
  int lcp_pfc;
  int tcp_mss_clamp_logged;
} ppp_session_t;

#define L2TP_DISPATCH_ERROR (-1)
#define L2TP_DISPATCH_OK 0
#define L2TP_DISPATCH_REMOTE_CLOSED 1

int l2tp_handshake(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len, l2tp_session_t *s);
int l2tp_data_extract_ppp(const uint8_t *plain, size_t plain_len, const l2tp_session_t *s, const uint8_t **ppp_out,
                          size_t *ppp_len_out, int diag);
int l2tp_dispatch_incoming(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                           l2tp_session_t *s, const uint8_t *data, size_t len, packet_endpoint_t *endpoint,
                           ppp_session_t *ppp);
int l2tp_send_ppp(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len, l2tp_session_t *s,
                  const uint8_t *ppp, size_t ppp_len);
int l2tp_send_teardown(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len, l2tp_session_t *s);

#endif
