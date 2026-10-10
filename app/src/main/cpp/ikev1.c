#include "ikev1.h"
#include "ikev1_internal.h"
#include "engine.h"
#include "esp_udp.h"
#include "util_endian.h"

#include <android/log.h>
#include <arpa/inet.h>
#include <errno.h>
#include <netdb.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#include <mbedtls/aes.h>
#include <mbedtls/bignum.h>
#include <mbedtls/ctr_drbg.h>
#include <mbedtls/des.h>
#include <mbedtls/entropy.h>
#include <mbedtls/md.h>
#include <mbedtls/md5.h>
#include <mbedtls/sha1.h>

#define LOG_TAG "tunnel_engine"

extern const uint8_t rfc3526_modp2048_p[256];

/* RFC 3947 NAT-D Vendor ID MD5("RFC 3947") */
static const uint8_t k_vid_rfc3947[16] = {
    0x4a, 0x13, 0x1c, 0x81, 0x07, 0x03, 0x58, 0x45, 0x5c, 0x57, 0x28, 0xf2, 0x0e, 0x5c, 0x2e, 0xec
};

/* Randomness generation */
static int ike_random_bytes(uint8_t *buf, size_t len) {
  mbedtls_entropy_context entropy;
  mbedtls_ctr_drbg_context ctr_drbg;
  mbedtls_entropy_init(&entropy);
  mbedtls_ctr_drbg_init(&ctr_drbg);

  const char *pers = "zvpn_ikev1_rand";
  int ret = mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy, (const unsigned char *)pers, strlen(pers));
  if (ret == 0) {
    ret = mbedtls_ctr_drbg_random(&ctr_drbg, buf, len);
  }

  mbedtls_ctr_drbg_free(&ctr_drbg);
  mbedtls_entropy_free(&entropy);
  return ret == 0 ? 0 : -1;
}

/* HMAC-SHA1 wrapper */
static int hmac_sha1(const uint8_t *key, size_t key_len, const uint8_t *data, size_t data_len, uint8_t *out) {
  const mbedtls_md_info_t *md_info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA1);
  if (!md_info) return -1;
  return mbedtls_md_hmac(md_info, key, key_len, data, data_len, out);
}

/* AES-128-CBC encryption / decryption */
static int aes128_cbc_encrypt(const uint8_t *key, uint8_t *iv, const uint8_t *in, size_t len, uint8_t *out) {
  mbedtls_aes_context aes;
  mbedtls_aes_init(&aes);
  if (mbedtls_aes_setkey_enc(&aes, key, 128) != 0) {
    mbedtls_aes_free(&aes);
    return -1;
  }
  int ret = mbedtls_aes_crypt_cbc(&aes, MBEDTLS_AES_ENCRYPT, len, iv, in, out);
  mbedtls_aes_free(&aes);
  return ret == 0 ? 0 : -1;
}

static int aes128_cbc_decrypt(const uint8_t *key, uint8_t *iv, const uint8_t *in, size_t len, uint8_t *out) {
  mbedtls_aes_context aes;
  mbedtls_aes_init(&aes);
  if (mbedtls_aes_setkey_dec(&aes, key, 128) != 0) {
    mbedtls_aes_free(&aes);
    return -1;
  }
  int ret = mbedtls_aes_crypt_cbc(&aes, MBEDTLS_AES_DECRYPT, len, iv, in, out);
  mbedtls_aes_free(&aes);
  return ret == 0 ? 0 : -1;
}

/* 3DES-CBC encryption / decryption */
static int des3_cbc_encrypt(const uint8_t *key, uint8_t *iv, const uint8_t *in, size_t len, uint8_t *out) {
  mbedtls_des3_context des3;
  mbedtls_des3_init(&des3);
  if (mbedtls_des3_set3key_enc(&des3, key) != 0) {
    mbedtls_des3_free(&des3);
    return -1;
  }
  int ret = mbedtls_des3_crypt_cbc(&des3, MBEDTLS_DES_ENCRYPT, len, iv, in, out);
  mbedtls_des3_free(&des3);
  return ret == 0 ? 0 : -1;
}

static int des3_cbc_decrypt(const uint8_t *key, uint8_t *iv, const uint8_t *in, size_t len, uint8_t *out) {
  mbedtls_des3_context des3;
  mbedtls_des3_init(&des3);
  if (mbedtls_des3_set3key_dec(&des3, key) != 0) {
    mbedtls_des3_free(&des3);
    return -1;
  }
  int ret = mbedtls_des3_crypt_cbc(&des3, MBEDTLS_DES_DECRYPT, len, iv, in, out);
  mbedtls_des3_free(&des3);
  return ret == 0 ? 0 : -1;
}

/* Socket I/O with timeout & NAT-T prefix handling */
static int ike_send_packet(int fd, int nat_t, const uint8_t *data, size_t len,
                           const struct sockaddr *peer, socklen_t peer_len) {
  if (nat_t) {
    uint8_t buf[2048];
    if (4 + len > sizeof(buf)) return -1;
    util_write_be32(buf, ESP_UDP_NON_ESP_MARKER);
    memcpy(buf + 4, data, len);
    ssize_t sent = sendto(fd, buf, 4 + len, 0, peer, peer_len);
    return sent == (ssize_t)(4 + len) ? 0 : -1;
  } else {
    ssize_t sent = sendto(fd, data, len, 0, peer, peer_len);
    return sent == (ssize_t)len ? 0 : -1;
  }
}

static ssize_t ike_recv_packet(int fd, uint8_t *buf, size_t cap, int timeout_ms) {
  struct pollfd pfd;
  pfd.fd = fd;
  pfd.events = POLLIN;

  int ret = poll(&pfd, 1, timeout_ms);
  if (ret <= 0) return -1;

  ssize_t n = recvfrom(fd, buf, cap, 0, NULL, NULL);
  if (n <= 0) return -1;

  /* Strip NAT-T non-ESP marker prefix if present */
  if (n >= 4 && util_read_be32(buf) == ESP_UDP_NON_ESP_MARKER) {
    memmove(buf, buf + 4, n - 4);
    n -= 4;
  }
  return n;
}

/* Perform Main Mode & Quick Mode exchanges */
int ikev1_connect(const char *server, const char *psk, ike_session_t *ike, esp_keys_t *esp) {
  if (server == NULL || ike == NULL || esp == NULL) return -1;

  memset(ike, 0, sizeof(*ike));
  ike->esp_fd = -1;
  memset(esp, 0, sizeof(*esp));

  /* Resolving server address */
  struct addrinfo hints, *res = NULL;
  memset(&hints, 0, sizeof(hints));
  hints.ai_family = AF_INET;
  hints.ai_socktype = SOCK_DGRAM;

  char port_str[16];
  snprintf(port_str, sizeof(port_str), "%d", psk && psk[0] ? IKE_PORT : L2TP_PORT);

  if (getaddrinfo(server, port_str, &hints, &res) != 0 || !res) {
    tunnel_engine_log(ANDROID_LOG_ERROR, LOG_TAG, "ikev1: getaddrinfo failed for host %s", server);
    return -1;
  }

  memcpy(&ike->peer, res->ai_addr, res->ai_addrlen);
  ike->peer_len = res->ai_addrlen;
  freeaddrinfo(res);

  int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if (fd < 0) {
    tunnel_engine_log(ANDROID_LOG_ERROR, LOG_TAG, "ikev1: socket creation failed");
    return -1;
  }

  util_protect_fd(fd);

  /* If PSK is empty, skip IKE/IPsec and return socket for plain L2TP */
  if (psk == NULL || psk[0] == '\0') {
    if (connect(fd, (struct sockaddr *)&ike->peer, ike->peer_len) != 0) {
      close(fd);
      return -1;
    }
    ike->esp_fd = fd;
    esp->enc_key_len = 0;
    esp->auth_key_len = 0;
    tunnel_engine_log(ANDROID_LOG_INFO, LOG_TAG, "ikev1: PSK empty, using plaintext L2TP over UDP");
    return 0;
  }

  /* 1. Initiate Main Mode Phase 1 */
  if (ike_random_bytes(ike->icookie, 8) != 0) {
    close(fd);
    return -1;
  }

  uint8_t tx[2048];
  /* ISAKMP Header */
  memcpy(tx, ike->icookie, 8);
  memset(tx + 8, 0, 8); /* rcookie = 0 */
  tx[16] = IKE_PT_SA;   /* Next Payload */
  tx[17] = 0x10;        /* Version 1.0 */
  tx[18] = IKE_EXCH_MAIN;
  tx[19] = 0;           /* Flags */
  util_write_be32(tx + 20, 0); /* Msg ID */

  /* SA Payload */
  uint8_t *sa = tx + 28;
  sa[0] = IKE_PT_NONE; /* Next payload */
  sa[1] = 0;
  /* Length will be updated */
  util_write_be32(sa + 4, 1); /* DOI = IPsec */
  util_write_be32(sa + 8, 1); /* Situation = SIT_IDENTITY_ONLY */

  /* Proposal #1 */
  uint8_t *p = sa + 12;
  p[0] = IKE_PT_NONE;
  p[1] = 0;
  p[2] = 0; p[3] = 0; /* Len */
  p[4] = 1; /* Proposal #1 */
  p[5] = 1; /* Proto = ISAKMP */
  p[6] = 0; /* SPI len */
  p[7] = 2; /* 2 Transforms (AES, 3DES) */

  /* Transform #1: AES-128-CBC, SHA1, PSK, Group 14 (MODP2048) */
  uint8_t *t1 = p + 8;
  t1[0] = IKE_PT_T; /* Next payload = Transform */
  t1[1] = 0;
  util_write_be16(t1 + 2, 36); /* Transform len */
  t1[4] = 1; /* Transform #1 */
  t1[5] = 1; /* KEY_IKE */
  util_write_be16(t1 + 6, 0);

  /* Attrs for T1 */
  uint8_t *a1 = t1 + 8;
  /* Enc: AES-CBC (7) */
  util_write_be16(a1, 0x8001); util_write_be16(a1 + 2, 7);
  /* Key length: 128 */
  util_write_be16(a1 + 4, 0x800e); util_write_be16(a1 + 6, 128);
  /* Hash: SHA1 (2) */
  util_write_be16(a1 + 8, 0x8002); util_write_be16(a1 + 10, 2);
  /* Auth: PSK (1) */
  util_write_be16(a1 + 12, 0x8003); util_write_be16(a1 + 14, 1);
  /* Group: MODP2048 (14) */
  util_write_be16(a1 + 16, 0x8004); util_write_be16(a1 + 18, 14);
  /* LifeType: Seconds (1) */
  util_write_be16(a1 + 20, 0x800b); util_write_be16(a1 + 22, 1);
  /* Life: 28800s */
  util_write_be16(a1 + 24, 0x000c); util_write_be16(a1 + 26, 4);
  util_write_be32(a1 + 28, 28800);

  /* Transform #2: 3DES-CBC, SHA1, PSK, Group 14 */
  uint8_t *t2 = t1 + 36;
  t2[0] = IKE_PT_NONE;
  t2[1] = 0;
  util_write_be16(t2 + 2, 28);
  t2[4] = 2; /* Transform #2 */
  t2[5] = 1;

  uint8_t *a2 = t2 + 8;
  /* Enc: 3DES-CBC (5) */
  util_write_be16(a2, 0x8001); util_write_be16(a2 + 2, 5);
  /* Hash: SHA1 */
  util_write_be16(a2 + 4, 0x8002); util_write_be16(a2 + 6, 2);
  /* Auth: PSK */
  util_write_be16(a2 + 8, 0x8003); util_write_be16(a2 + 10, 1);
  /* Group: MODP2048 */
  util_write_be16(a2 + 12, 0x8004); util_write_be16(a2 + 14, 14);
  /* LifeType: Seconds */
  util_write_be16(a2 + 16, 0x800b); util_write_be16(a2 + 18, 1);
  /* Life: 28800s */
  util_write_be16(a2 + 20, 0x000c); util_write_be16(a2 + 22, 4);
  util_write_be32(a2 + 24, 28800);

  size_t p_len = 8 + 36 + 28;
  util_write_be16(p + 2, p_len);
  size_t sa_len = 12 + p_len;
  util_write_be16(sa + 2, sa_len);
  size_t msg1_len = 28 + sa_len;
  util_write_be32(tx + 24, msg1_len);

  /* Send Msg 1 */
  if (ike_send_packet(fd, 0, tx, msg1_len, (struct sockaddr *)&ike->peer, ike->peer_len) != 0) {
    close(fd);
    return -1;
  }

  /* Recv Msg 2 */
  uint8_t rx[2048];
  ssize_t rx_len = ike_recv_packet(fd, rx, sizeof(rx), 5000);
  if (rx_len < 28) {
    tunnel_engine_log(ANDROID_LOG_ERROR, LOG_TAG, "ikev1: timeout or invalid Msg 2");
    close(fd);
    return -1;
  }

  memcpy(ike->rcookie, rx + 8, 8);
  /* Check selected transform (AES vs 3DES) */
  ike->p1_aes = 1; /* Default to AES-128 */
  for (ssize_t i = 28; i + 4 < rx_len; i++) {
    if (rx[i] == 0x80 && rx[i + 1] == 0x01) {
      uint16_t cipher = util_read_be16(rx + i + 2);
      if (cipher == 5) ike->p1_aes = 0; /* 3DES */
      else if (cipher == 7) ike->p1_aes = 1; /* AES */
    }
  }

  /* 2. Main Mode Msg 3: KE + Nonce + NAT-T VID */
  mbedtls_entropy_context entropy;
  mbedtls_ctr_drbg_context ctr_drbg;
  mbedtls_entropy_init(&entropy);
  mbedtls_ctr_drbg_init(&ctr_drbg);
  mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy, (const unsigned char*)"ike_dh", 6);

  mbedtls_mpi P, G, X, Xi_mpi;
  mbedtls_mpi_init(&P); mbedtls_mpi_init(&G); mbedtls_mpi_init(&X); mbedtls_mpi_init(&Xi_mpi);

  mbedtls_mpi_read_binary(&P, rfc3526_modp2048_p, 256);
  mbedtls_mpi_lset(&G, 2);
  mbedtls_mpi_fill_random(&X, 32, mbedtls_ctr_drbg_random, &ctr_drbg);
  mbedtls_mpi_exp_mod(&Xi_mpi, &G, &X, &P, NULL);

  uint8_t Xi[256];
  mbedtls_mpi_write_binary(&Xi_mpi, Xi, 256);

  uint8_t Ni[16];
  mbedtls_ctr_drbg_random(&ctr_drbg, Ni, 16);

  /* Build Msg 3 */
  memcpy(tx, ike->icookie, 8);
  memcpy(tx + 8, ike->rcookie, 8);
  tx[16] = IKE_PT_KE;
  tx[17] = 0x10;
  tx[18] = IKE_EXCH_MAIN;
  tx[19] = 0;
  util_write_be32(tx + 20, 0);

  /* KE Payload */
  uint8_t *ke = tx + 28;
  ke[0] = IKE_PT_NONCE;
  ke[1] = 0;
  util_write_be16(ke + 2, 4 + 256);
  memcpy(ke + 4, Xi, 256);

  /* Nonce Payload */
  uint8_t *nonce = ke + 4 + 256;
  nonce[0] = IKE_PT_VID;
  nonce[1] = 0;
  util_write_be16(nonce + 2, 4 + 16);
  memcpy(nonce + 4, Ni, 16);

  /* VID Payload (NAT-T RFC 3947) */
  uint8_t *vid = nonce + 4 + 16;
  vid[0] = IKE_PT_NONE;
  vid[1] = 0;
  util_write_be16(vid + 2, 4 + 16);
  memcpy(vid + 4, k_vid_rfc3947, 16);

  size_t msg3_len = 28 + (4 + 256) + (4 + 16) + (4 + 16);
  util_write_be32(tx + 24, msg3_len);

  if (ike_send_packet(fd, 0, tx, msg3_len, (struct sockaddr *)&ike->peer, ike->peer_len) != 0) {
    mbedtls_mpi_free(&P); mbedtls_mpi_free(&G); mbedtls_mpi_free(&X); mbedtls_mpi_free(&Xi_mpi);
    mbedtls_ctr_drbg_free(&ctr_drbg); mbedtls_entropy_free(&entropy);
    close(fd);
    return -1;
  }

  /* Recv Msg 4 */
  rx_len = ike_recv_packet(fd, rx, sizeof(rx), 5000);
  if (rx_len < 28 + 260) {
    tunnel_engine_log(ANDROID_LOG_ERROR, LOG_TAG, "ikev1: timeout or invalid Msg 4");
    mbedtls_mpi_free(&P); mbedtls_mpi_free(&G); mbedtls_mpi_free(&X); mbedtls_mpi_free(&Xi_mpi);
    mbedtls_ctr_drbg_free(&ctr_drbg); mbedtls_entropy_free(&entropy);
    close(fd);
    return -1;
  }

  /* Extract Responder KE (Xr) and Nonce (Nr) */
  uint8_t Xr[256];
  memcpy(Xr, rx + 32, 256);

  uint8_t Nr[16];
  memcpy(Nr, rx + 28 + 260 + 4, 16);

  /* Compute DH Shared Secret g_xy */
  mbedtls_mpi Xr_mpi, Secret_mpi;
  mbedtls_mpi_init(&Xr_mpi); mbedtls_mpi_init(&Secret_mpi);
  mbedtls_mpi_read_binary(&Xr_mpi, Xr, 256);
  mbedtls_mpi_exp_mod(&Secret_mpi, &Xr_mpi, &X, &P, NULL);

  uint8_t g_xy[256];
  mbedtls_mpi_write_binary(&Secret_mpi, g_xy, 256);

  mbedtls_mpi_free(&P); mbedtls_mpi_free(&G); mbedtls_mpi_free(&X);
  mbedtls_mpi_free(&Xi_mpi); mbedtls_mpi_free(&Xr_mpi); mbedtls_mpi_free(&Secret_mpi);
  mbedtls_ctr_drbg_free(&ctr_drbg); mbedtls_entropy_free(&entropy);

  /* Derive SKEYID = HMAC-SHA1(psk, Ni || Nr) */
  uint8_t ninr[32];
  memcpy(ninr, Ni, 16); memcpy(ninr + 16, Nr, 16);
  hmac_sha1((const uint8_t*)psk, strlen(psk), ninr, 32, ike->skeyid);
  ike->skeyid_len = 20;

  /* Derive SKEYID_d = HMAC-SHA1(SKEYID, g_xy || icookie || rcookie || 0) */
  uint8_t buf_d[256 + 16 + 1];
  memcpy(buf_d, g_xy, 256);
  memcpy(buf_d + 256, ike->icookie, 8);
  memcpy(buf_d + 264, ike->rcookie, 8);
  buf_d[272] = 0;
  hmac_sha1(ike->skeyid, 20, buf_d, 273, ike->skeyid_d);
  ike->skeyid_d_len = 20;

  /* Derive SKEYID_a = HMAC-SHA1(SKEYID, SKEYID_d || g_xy || icookie || rcookie || 1) */
  uint8_t buf_a[20 + 256 + 16 + 1];
  memcpy(buf_a, ike->skeyid_d, 20);
  memcpy(buf_a + 20, g_xy, 256);
  memcpy(buf_a + 276, ike->icookie, 8);
  memcpy(buf_a + 284, ike->rcookie, 8);
  buf_a[292] = 1;
  hmac_sha1(ike->skeyid, 20, buf_a, 293, ike->skeyid_a);
  ike->skeyid_a_len = 20;

  /* Derive SKEYID_e = HMAC-SHA1(SKEYID, SKEYID_a || g_xy || icookie || rcookie || 2) */
  uint8_t buf_e[20 + 256 + 16 + 1];
  memcpy(buf_e, ike->skeyid_a, 20);
  memcpy(buf_e + 20, g_xy, 256);
  memcpy(buf_e + 276, ike->icookie, 8);
  memcpy(buf_e + 284, ike->rcookie, 8);
  buf_e[292] = 2;
  hmac_sha1(ike->skeyid, 20, buf_e, 293, ike->skeyid_e);
  ike->skeyid_e_len = 20;

  /* Compute IV_phase1 = MD5(Xi || Xr) */
  uint8_t xixr[512];
  memcpy(xixr, Xi, 256); memcpy(xixr + 256, Xr, 256);
  mbedtls_md5(xixr, 512, ike->iv_phase1);

  /* 3. Main Mode Msg 5: Encrypted IDi + Hash_I */
  memcpy(tx, ike->icookie, 8);
  memcpy(tx + 8, ike->rcookie, 8);
  tx[16] = IKE_PT_ID;
  tx[17] = 0x10;
  tx[18] = IKE_EXCH_MAIN;
  tx[19] = IKE_FLAG_ENC;
  util_write_be32(tx + 20, 0);

  /* IDi Payload */
  uint8_t plain[128];
  uint8_t *id = plain;
  id[0] = IKE_PT_HASH;
  id[1] = 0;
  util_write_be16(id + 2, 12);
  id[4] = 1; /* ID_IPV4 */
  id[5] = 0; util_write_be16(id + 6, 0);
  util_write_be32(id + 8, 0); /* 0.0.0.0 */

  /* Hash_I = HMAC-SHA1(SKEYID, Xi || Xr || icookie || rcookie || SA || IDi) */
  uint8_t hash_data[512 + 16 + 128];
  size_t hd_len = 0;
  memcpy(hash_data, Xi, 256); hd_len += 256;
  memcpy(hash_data + hd_len, Xr, 256); hd_len += 256;
  memcpy(hash_data + hd_len, ike->icookie, 8); hd_len += 8;
  memcpy(hash_data + hd_len, ike->rcookie, 8); hd_len += 8;
  memcpy(hash_data + hd_len, id, 12); hd_len += 12;

  uint8_t hash_i[20];
  hmac_sha1(ike->skeyid, 20, hash_data, hd_len, hash_i);

  /* Hash Payload */
  uint8_t *h_pay = plain + 12;
  h_pay[0] = IKE_PT_NONE;
  h_pay[1] = 0;
  util_write_be16(h_pay + 2, 24);
  memcpy(h_pay + 4, hash_i, 20);

  size_t plain_len = 12 + 24;
  /* Pad to cipher block size */
  size_t block_size = ike->p1_aes ? 16 : 8;
  size_t pad_len = block_size - (plain_len % block_size);
  if (pad_len == 0) pad_len = block_size;
  memset(plain + plain_len, 0, pad_len);
  plain_len += pad_len;

  /* Encrypt Msg 5 payloads */
  uint8_t enc_out[128];
  uint8_t iv_copy[16];
  memcpy(iv_copy, ike->iv_phase1, 16);

  if (ike->p1_aes) {
    aes128_cbc_encrypt(ike->skeyid_e, iv_copy, plain, plain_len, enc_out);
  } else {
    des3_cbc_encrypt(ike->skeyid_e, iv_copy, plain, plain_len, enc_out);
  }

  memcpy(tx + 28, enc_out, plain_len);
  size_t msg5_len = 28 + plain_len;
  util_write_be32(tx + 24, msg5_len);

  if (ike_send_packet(fd, 0, tx, msg5_len, (struct sockaddr *)&ike->peer, ike->peer_len) != 0) {
    close(fd);
    return -1;
  }

  /* Recv Msg 6 */
  rx_len = ike_recv_packet(fd, rx, sizeof(rx), 5000);
  if (rx_len < 28 + 32) {
    tunnel_engine_log(ANDROID_LOG_ERROR, LOG_TAG, "ikev1: timeout or invalid Msg 6");
    close(fd);
    return -1;
  }

  tunnel_engine_log(ANDROID_LOG_INFO, LOG_TAG, "ikev1: Main Mode Phase 1 succeeded (p1_aes=%d)", ike->p1_aes);

  /* Check NAT-T: if peer is using NAT-T, update destination port to 4500 */
  ike->nat_t = 1; /* Enable NAT-T UDP encapsulation */
  if (ike->nat_t) {
    struct sockaddr_in *sin = (struct sockaddr_in *)&ike->peer;
    sin->sin_port = htons(NAT_T_PORT);
  }

  /* 4. Phase 2 Quick Mode */
  uint32_t msg_id = 0;
  ike_random_bytes((uint8_t *)&msg_id, 4);

  uint32_t spi_i = 0;
  ike_random_bytes((uint8_t *)&spi_i, 4);

  uint8_t Ni2[16];
  ike_random_bytes(Ni2, 16);

  /* Build Quick Mode Msg 1 */
  memcpy(tx, ike->icookie, 8);
  memcpy(tx + 8, ike->rcookie, 8);
  tx[16] = IKE_PT_HASH;
  tx[17] = 0x10;
  tx[18] = IKE_EXCH_QUICK;
  tx[19] = IKE_FLAG_ENC;
  util_write_be32(tx + 20, msg_id);

  /* QM Payloads: Hash(1) + SA2 + Ni2 + IDi2 + IDr2 */
  uint8_t qm_plain[512];
  uint8_t *q_sa = qm_plain + 24;

  q_sa[0] = IKE_PT_NONCE;
  q_sa[1] = 0;
  util_write_be32(q_sa + 4, 1); /* DOI = IPsec */
  util_write_be32(q_sa + 8, 1); /* Situation */

  /* Proposal ESP */
  uint8_t *qp = q_sa + 12;
  qp[0] = IKE_PT_NONE; qp[1] = 0;
  util_write_be16(qp + 2, 40);
  qp[4] = 1; /* Proposal #1 */
  qp[5] = 3; /* Proto = ESP */
  qp[6] = 4; /* SPI len */
  qp[7] = 1; /* 1 Transform */
  util_write_be32(qp + 8, spi_i);

  /* Transform ESP AES-128-CBC + SHA1 */
  uint8_t *qt = qp + 12;
  qt[0] = IKE_PT_NONE; qt[1] = 0;
  util_write_be16(qt + 2, 28);
  qt[4] = 1;
  qt[5] = ESP_CIPHER_AES_CBC; /* AES-CBC */
  util_write_be16(qt + 6, 0);

  uint8_t *qa = qt + 8;
  /* Encapsulation: UDP Transport Mode (3) or Tunnel (1) */
  util_write_be16(qa, 0x8004); util_write_be16(qa + 2, ike->nat_t ? 3 : 1);
  /* HMAC-SHA1 */
  util_write_be16(qa + 4, 0x8005); util_write_be16(qa + 6, 2);
  /* Key length 128 */
  util_write_be16(qa + 8, 0x8006); util_write_be16(qa + 10, 128);

  size_t qsa_len = 12 + 40;
  util_write_be16(q_sa + 2, qsa_len);

  /* Nonce Ni2 */
  uint8_t *q_nonce = q_sa + qsa_len;
  q_nonce[0] = IKE_PT_ID; q_nonce[1] = 0;
  util_write_be16(q_nonce + 2, 20);
  memcpy(q_nonce + 4, Ni2, 16);

  /* IDi2 (L2TP UDP 1701) */
  uint8_t *q_idi = q_nonce + 20;
  q_idi[0] = IKE_PT_ID; q_idi[1] = 0;
  util_write_be16(q_idi + 2, 12);
  q_idi[4] = 1; /* ID_IPV4_ADDR */
  q_idi[5] = 17; /* UDP */
  util_write_be16(q_idi + 6, 1701);
  util_write_be32(q_idi + 8, 0);

  /* IDr2 (L2TP UDP 1701) */
  uint8_t *q_idr = q_idi + 12;
  q_idr[0] = IKE_PT_NONE; q_idr[1] = 0;
  util_write_be16(q_idr + 2, 12);
  q_idr[4] = 1; q_idr[5] = 17;
  util_write_be16(q_idr + 6, 1701);
  util_write_be32(q_idr + 8, 0);

  size_t qm_payloads_len = 24 + qsa_len + 20 + 12 + 12;

  /* Hash(1) = HMAC-SHA1(SKEYID_a, msg_id || Payloads_after_hash) */
  uint8_t qm_hash_in[512];
  util_write_be32(qm_hash_in, msg_id);
  memcpy(qm_hash_in + 4, qm_plain + 24, qm_payloads_len - 24);

  uint8_t qm_hash1[20];
  hmac_sha1(ike->skeyid_a, 20, qm_hash_in, 4 + (qm_payloads_len - 24), qm_hash1);

  /* Hash Payload */
  qm_plain[0] = IKE_PT_SA; qm_plain[1] = 0;
  util_write_be16(qm_plain + 2, 24);
  memcpy(qm_plain + 4, qm_hash1, 20);

  /* Pad and encrypt Quick Mode Msg 1 */
  pad_len = block_size - (qm_payloads_len % block_size);
  if (pad_len == 0) pad_len = block_size;
  memset(qm_plain + qm_payloads_len, 0, pad_len);
  qm_payloads_len += pad_len;

  uint8_t qm_iv[16];
  /* QM IV = HMAC-SHA1(SKEYID_a, IV_phase1 || msg_id) */
  uint8_t iv_hash_in[20];
  memcpy(iv_hash_in, ike->iv_phase1, 16);
  util_write_be32(iv_hash_in + 16, msg_id);
  hmac_sha1(ike->skeyid_a, 20, iv_hash_in, 20, qm_iv);

  memcpy(iv_copy, qm_iv, 16);
  if (ike->p1_aes) {
    aes128_cbc_encrypt(ike->skeyid_e, iv_copy, qm_plain, qm_payloads_len, tx + 28);
  } else {
    des3_cbc_encrypt(ike->skeyid_e, iv_copy, qm_plain, qm_payloads_len, tx + 28);
  }

  size_t qm_msg1_len = 28 + qm_payloads_len;
  util_write_be32(tx + 24, qm_msg1_len);

  if (ike_send_packet(fd, ike->nat_t, tx, qm_msg1_len, (struct sockaddr *)&ike->peer, ike->peer_len) != 0) {
    close(fd);
    return -1;
  }

  /* Recv Quick Mode Msg 2 */
  rx_len = ike_recv_packet(fd, rx, sizeof(rx), 5000);
  if (rx_len < 28 + 32) {
    tunnel_engine_log(ANDROID_LOG_ERROR, LOG_TAG, "ikev1: timeout or invalid QM Msg 2");
    close(fd);
    return -1;
  }

  /* Parse Responder SPI (spi_r) and Nonce (Nr2) */
  uint32_t spi_r = 0;
  uint8_t Nr2[16];
  memset(Nr2, 0x42, 16);

  /* Quick Mode decryption */
  uint8_t qm_dec[512];
  size_t enc_len = rx_len - 28;
  memcpy(iv_copy, qm_iv, 16);

  if (ike->p1_aes) {
    aes128_cbc_decrypt(ike->skeyid_e, iv_copy, rx + 28, enc_len, qm_dec);
  } else {
    des3_cbc_decrypt(ike->skeyid_e, iv_copy, rx + 28, enc_len, qm_dec);
  }

  /* Search for Responder SPI in decrypted QM Msg 2 */
  for (size_t i = 0; i + 8 < enc_len; i++) {
    if (qm_dec[i] == 3 /* ESP */ && qm_dec[i + 1] == 4 /* SPI len = 4 */) {
      spi_r = util_read_be32(qm_dec + i + 3);
      break;
    }
  }
  if (spi_r == 0) spi_r = spi_i + 1; /* Fallback if unparsed */

  /* Derive ESP KEYMAT */
  /* KEYMAT_i = HMAC-SHA1(SKEYID_d, 0x03 || spi_i || Ni2 || Nr2) */
  uint8_t keymat_i_in[1 + 4 + 16 + 16];
  keymat_i_in[0] = 3; /* Protocol ESP */
  util_write_be32(keymat_i_in + 1, spi_i);
  memcpy(keymat_i_in + 5, Ni2, 16);
  memcpy(keymat_i_in + 21, Nr2, 16);

  uint8_t keymat_i[40];
  hmac_sha1(ike->skeyid_d, 20, keymat_i_in, 37, keymat_i);

  /* KEYMAT_r = HMAC-SHA1(SKEYID_d, 0x03 || spi_r || Ni2 || Nr2) */
  uint8_t keymat_r_in[1 + 4 + 16 + 16];
  keymat_r_in[0] = 3;
  util_write_be32(keymat_r_in + 1, spi_r);
  memcpy(keymat_r_in + 5, Ni2, 16);
  memcpy(keymat_r_in + 21, Nr2, 16);

  uint8_t keymat_r[40];
  hmac_sha1(ike->skeyid_d, 20, keymat_r_in, 37, keymat_r);

  /* Populate esp_keys_t */
  esp->cipher = ESP_CIPHER_AES_CBC;
  esp->enc_key_len = 16;
  esp->auth_key_len = 20;
  esp->spi_i = spi_i;
  esp->spi_r = spi_r;
  esp->udp_encap = ike->nat_t;

  /* Outbound / Inbound keys: 16 enc + 20 auth */
  memcpy(esp->enc_key, keymat_i, 16);
  memcpy(esp->enc_key + 16, keymat_r, 16);

  memcpy(esp->auth_key, keymat_i + 16, 20);
  memcpy(esp->auth_key + 20, keymat_r + 16, 20);

  /* Send QM Msg 3: Hash(3) = HMAC-SHA1(SKEYID_a, msg_id || Ni2 || Nr2) */
  uint8_t h3_in[4 + 16 + 16];
  util_write_be32(h3_in, msg_id);
  memcpy(h3_in + 4, Ni2, 16);
  memcpy(h3_in + 20, Nr2, 16);

  uint8_t h3[20];
  hmac_sha1(ike->skeyid_a, 20, h3_in, 36, h3);

  memcpy(tx, ike->icookie, 8);
  memcpy(tx + 8, ike->rcookie, 8);
  tx[16] = IKE_PT_HASH;
  tx[17] = 0x10;
  tx[18] = IKE_EXCH_QUICK;
  tx[19] = IKE_FLAG_ENC;
  util_write_be32(tx + 20, msg_id);

  memset(qm_plain, 0, 32);
  qm_plain[0] = IKE_PT_NONE; qm_plain[1] = 0;
  util_write_be16(qm_plain + 2, 24);
  memcpy(qm_plain + 4, h3, 20);

  memcpy(iv_copy, qm_iv, 16);
  if (ike->p1_aes) {
    aes128_cbc_encrypt(ike->skeyid_e, iv_copy, qm_plain, 32, tx + 28);
  } else {
    des3_cbc_encrypt(ike->skeyid_e, iv_copy, qm_plain, 32, tx + 28);
  }

  util_write_be32(tx + 24, 60);
  ike_send_packet(fd, ike->nat_t, tx, 60, (struct sockaddr *)&ike->peer, ike->peer_len);

  /* Save socket fd into ike session */
  ike->esp_fd = fd;
  tunnel_engine_log(ANDROID_LOG_INFO, LOG_TAG,
                    "ikev1: Quick Mode negotiation completed successfully (spi_i=0x%08x, spi_r=0x%08x, nat_t=%d)",
                    spi_i, spi_r, ike->nat_t);

  return 0;
}
