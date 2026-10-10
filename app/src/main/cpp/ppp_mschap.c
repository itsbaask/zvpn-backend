#include <stdint.h>
#include <stddef.h>
#include <string.h>

#include <mbedtls/des.h>
#include <mbedtls/md4.h>
#include <mbedtls/sha1.h>

/* Generates NT Password Hash (MD4 over UTF-16LE password) */
static void nt_password_hash(const char *password, uint8_t *hash_out) {
  uint8_t unicode_pwd[512];
  size_t len = strlen(password);
  if (len > 255) len = 255;

  for (size_t i = 0; i < len; i++) {
    unicode_pwd[i * 2] = (uint8_t)password[i];
    unicode_pwd[i * 2 + 1] = 0;
  }

  mbedtls_md4(unicode_pwd, len * 2, hash_out);
}

/* DES single-block encryption helper */
static void des_encrypt_block(const uint8_t key_7bytes[7], const uint8_t in[8], uint8_t out[8]) {
  uint8_t key_8bytes[8];
  /* Expand 7-byte key to 8-byte parity DES key */
  key_8bytes[0] = key_7bytes[0];
  key_8bytes[1] = (key_7bytes[0] << 7) | (key_7bytes[1] >> 1);
  key_8bytes[2] = (key_7bytes[1] << 6) | (key_7bytes[2] >> 2);
  key_8bytes[3] = (key_7bytes[2] << 5) | (key_7bytes[3] >> 3);
  key_8bytes[4] = (key_7bytes[3] << 4) | (key_7bytes[4] >> 4);
  key_8bytes[5] = (key_7bytes[4] << 3) | (key_7bytes[5] >> 5);
  key_8bytes[6] = (key_7bytes[5] << 2) | (key_7bytes[6] >> 6);
  key_8bytes[7] = key_7bytes[6] << 1;

  mbedtls_des_context des;
  mbedtls_des_init(&des);
  mbedtls_des_setkey_enc(&des, key_8bytes);
  mbedtls_des_crypt_ecb(&des, in, out);
  mbedtls_des_free(&des);
}

/* ChallengeResponse (RFC 2759) */
static void challenge_response(const uint8_t challenge[8], const uint8_t password_hash[16], uint8_t response[24]) {
  uint8_t padded_hash[21];
  memset(padded_hash, 0, 21);
  memcpy(padded_hash, password_hash, 16);

  des_encrypt_block(padded_hash, challenge, response);
  des_encrypt_block(padded_hash + 7, challenge, response + 8);
  des_encrypt_block(padded_hash + 14, challenge, response + 16);
}

/* MS-CHAPv2 Generate Response (RFC 2759) */
int ppp_mschapv2_generate_response(const uint8_t *authenticator_challenge, const uint8_t *peer_challenge,
                                  const char *username, const char *password, uint8_t response_out[24]) {
  if (!authenticator_challenge || !peer_challenge || !username || !password || !response_out) return -1;

  /* 1. ChallengeHash = SHA1(peer_challenge || authenticator_challenge || username)[0..7] */
  uint8_t challenge_hash[20];
  mbedtls_sha1_context sha1;
  mbedtls_sha1_init(&sha1);
  mbedtls_sha1_starts(&sha1);
  mbedtls_sha1_update(&sha1, peer_challenge, 16);
  mbedtls_sha1_update(&sha1, authenticator_challenge, 16);

  /* Extract username without domain prefix if present */
  const char *u = strrchr(username, '\\');
  if (u) u++; else u = username;

  mbedtls_sha1_update(&sha1, (const uint8_t *)u, strlen(u));
  mbedtls_sha1_finish(&sha1, challenge_hash);
  mbedtls_sha1_free(&sha1);

  /* 2. NtPasswordHash */
  uint8_t nt_hash[16];
  nt_password_hash(password, nt_hash);

  /* 3. ChallengeResponse */
  challenge_response(challenge_hash, nt_hash, response_out);

  return 0;
}
