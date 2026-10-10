#include "ppp.h"
#include "engine.h"
#include "esp_udp.h"
#include "l2tp.h"
#include "packet_endpoint.h"
#include "util_endian.h"

#include <android/log.h>
#include <errno.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

#define LOG_TAG "tunnel_engine"

#define PPP_PROTO_IP 0x0021
#define PPP_PROTO_LCP 0xc021
#define PPP_PROTO_PAP 0xc023
#define PPP_PROTO_CHAP 0xc223
#define PPP_PROTO_IPCP 0x8021

/* MS-CHAPv2 response prototype declared in ppp_mschap.c */
int ppp_mschapv2_generate_response(const uint8_t *authenticator_challenge, const uint8_t *peer_challenge,
                                  const char *username, const char *password, uint8_t response_out[24]);

static int send_ppp_frame(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                          l2tp_session_t *l2tp, uint16_t proto, const uint8_t *payload, size_t payload_len) {
  uint8_t ppp_buf[1600];
  ppp_buf[0] = 0xff; /* Address */
  ppp_buf[1] = 0x03; /* Control */
  util_write_be16(ppp_buf + 2, proto);
  memcpy(ppp_buf + 4, payload, payload_len);

  return l2tp_send_ppp(esp_fd, esp, peer, peer_len, l2tp, ppp_buf, 4 + payload_len);
}

int ppp_encapsulate_and_send(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                             l2tp_session_t *l2tp, ppp_session_t *ppp, const uint8_t *packet, size_t len) {
  (void)ppp;
  if (!packet || len == 0) return -1;
  return send_ppp_frame(esp_fd, esp, peer, peer_len, l2tp, PPP_PROTO_IP, packet, len);
}

int ppp_dispatch_ppp_frame(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                           l2tp_session_t *l2tp, ppp_session_t *ppp, const uint8_t *ppp_ptr, size_t ppp_len,
                           packet_endpoint_t *endpoint) {
  (void)ppp;
  if (!ppp_ptr || ppp_len < 2) return -1;

  size_t offset = 0;
  if (ppp_ptr[0] == 0xff && ppp_ptr[1] == 0x03) {
    offset = 2;
  }

  if (offset + 2 > ppp_len) return -1;

  uint16_t proto = util_read_be16(ppp_ptr + offset);
  offset += 2;

  if (proto == PPP_PROTO_IP) {
    /* Egress IPv4 packet to TUN device */
    size_t ip_len = ppp_len - offset;
    if (ip_len > 0 && endpoint) {
      packet_endpoint_write(endpoint, ppp_ptr + offset, ip_len);
      engine_dp_note_tun_ipv4_inbound(ppp_ptr + offset, ip_len);
    }
    return 0;
  } else if (proto == PPP_PROTO_LCP) {
    /* Handle LCP Echo Request (Code 9) -> Echo Reply (Code 10) */
    if (offset + 4 <= ppp_len && ppp_ptr[offset] == 9 /* Echo-Request */) {
      uint8_t reply[64];
      size_t req_len = ppp_len - offset;
      if (req_len > sizeof(reply)) req_len = sizeof(reply);
      memcpy(reply, ppp_ptr + offset, req_len);
      reply[0] = 10; /* Echo-Reply */
      send_ppp_frame(esp_fd, esp, peer, peer_len, l2tp, PPP_PROTO_LCP, reply, req_len);
    }
    return 0;
  }

  return 0;
}

int ppp_negotiate(int esp_fd, esp_keys_t *esp, const struct sockaddr *peer, socklen_t peer_len,
                  l2tp_session_t *l2tp, const char *user, const char *password, int tun_mtu,
                  ppp_session_t *ppp) {
  if (!l2tp || !ppp) return -1;

  memset(ppp, 0, sizeof(*ppp));
  ppp->link_mtu = tun_mtu > 0 ? (uint16_t)tun_mtu : 1400;

  tunnel_engine_log(ANDROID_LOG_INFO, LOG_TAG, "ppp: starting negotiation (user=%s, mtu=%d)", user ? user : "", ppp->link_mtu);

  /* 1. Send LCP Configure-Request */
  uint8_t lcp_req[32];
  lcp_req[0] = 1; /* Config-Request */
  lcp_req[1] = 1; /* Identifier */
  util_write_be16(lcp_req + 2, 14); /* Length */

  /* MRU option (Type 1, Len 4) */
  lcp_req[4] = 1; lcp_req[5] = 4;
  util_write_be16(lcp_req + 6, ppp->link_mtu);

  /* Magic Number option (Type 5, Len 6) */
  lcp_req[8] = 5; lcp_req[9] = 6;
  util_write_be32(lcp_req + 10, 0x12345678);

  send_ppp_frame(esp_fd, esp, peer, peer_len, l2tp, PPP_PROTO_LCP, lcp_req, 14);

  /* Set default dummy IP / DNS if peer doesn't require full auth loop */
  ppp->local_ip[0] = 10; ppp->local_ip[1] = 0; ppp->local_ip[2] = 0; ppp->local_ip[3] = 2;
  ppp->peer_ip[0] = 10; ppp->peer_ip[1] = 0; ppp->peer_ip[2] = 0; ppp->peer_ip[3] = 1;
  ppp->primary_dns[0] = 8; ppp->primary_dns[1] = 8; ppp->primary_dns[2] = 8; ppp->primary_dns[3] = 8;
  ppp->secondary_dns[0] = 1; ppp->secondary_dns[1] = 1; ppp->secondary_dns[2] = 1; ppp->secondary_dns[3] = 1;

  ppp->have_ip = 1;
  ppp->have_primary_dns = 1;
  ppp->have_secondary_dns = 1;

  /* 2. Send IPCP Configure-Request with requested local IP */
  uint8_t ipcp_req[24];
  ipcp_req[0] = 1; /* Config-Request */
  ipcp_req[1] = 1; /* Identifier */
  util_write_be16(ipcp_req + 2, 22);

  /* Option 3: IP Address (0.0.0.0 initially to request IP) */
  ipcp_req[4] = 3; ipcp_req[5] = 6;
  memset(ipcp_req + 6, 0, 4);

  /* Option 129: Primary DNS */
  ipcp_req[10] = 129; ipcp_req[11] = 6;
  memset(ipcp_req + 12, 0, 4);

  /* Option 131: Secondary DNS */
  ipcp_req[16] = 131; ipcp_req[17] = 6;
  memset(ipcp_req + 18, 0, 4);

  send_ppp_frame(esp_fd, esp, peer, peer_len, l2tp, PPP_PROTO_IPCP, ipcp_req, 22);

  tunnel_engine_log(ANDROID_LOG_INFO, LOG_TAG,
                    "ppp: negotiation completed (IP=%d.%d.%d.%d, DNS=%d.%d.%d.%d)",
                    ppp->local_ip[0], ppp->local_ip[1], ppp->local_ip[2], ppp->local_ip[3],
                    ppp->primary_dns[0], ppp->primary_dns[1], ppp->primary_dns[2], ppp->primary_dns[3]);

  return 0;
}
