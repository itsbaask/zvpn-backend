package com.zvpn.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual object ZvxCrypto {
    private val MAGIC = "ZVX1".toByteArray(Charsets.US_ASCII)
    private const val LEGACY_VERSION: Byte = 1
    private const val VERSION: Byte = 2
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_LEN = 32

    private const val ARGON_MEM_KIB = 65_536
    private const val ARGON_ITERATIONS = 3
    private const val ARGON_PARALLELISM = 1

    private const val CFG_AAD = "zsx-cfg-v1"

    private val openKey: ByteArray =
        MessageDigest.getInstance("SHA-256").digest("ZedSecure .zvx v2".toByteArray(Charsets.US_ASCII))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rng = SecureRandom()

    actual fun seal(request: ZvxSealRequest): ByteArray {
        val cfgNonce = randomBytes(NONCE_LEN)
        val mode: Int
        val kdf: ZvxKdf?
        val configKey: ByteArray
        if (request.password.isNullOrEmpty()) {
            mode = 0
            kdf = null
            configKey = openKey
        } else {
            mode = 1
            val salt = randomBytes(16)
            configKey = argon2(request.password, salt)
            kdf = ZvxKdf(Base64.encode(salt), ARGON_MEM_KIB, ARGON_ITERATIONS, ARGON_PARALLELISM)
        }

        val cfgCipher = aesGcmEncrypt(configKey, cfgNonce, CFG_AAD.toByteArray(), request.configPayload.toByteArray())

        val inner = ZvxInner(
            mode = mode,
            nameEn = request.nameEn,
            nameFa = request.nameFa,
            note = request.note,
            createdAt = System.currentTimeMillis(),
            expiresAt = request.expiresAt,
            kdf = kdf,
            cfgNonce = Base64.encode(cfgNonce),
            cfgCipher = Base64.encode(cfgCipher),
        )
        val innerBytes = json.encodeToString(ZvxInner.serializer(), inner).toByteArray()

        val outerNonce = randomBytes(NONCE_LEN)
        val outerCipher = aesGcmEncrypt(openKey, outerNonce, aad(), innerBytes)

        return MAGIC + byteArrayOf(VERSION) + outerNonce + outerCipher
    }

    actual fun peek(bytes: ByteArray): ZvxMetadata = readInner(bytes).let { inner ->
        ZvxMetadata(
            passwordProtected = inner.mode == 1,
            nameEn = inner.nameEn,
            nameFa = inner.nameFa,
            note = inner.note,
            createdAt = inner.createdAt,
            expiresAt = inner.expiresAt,
        )
    }

    actual fun open(bytes: ByteArray, password: String?): String {
        val inner = readInner(bytes)
        if (inner.expiresAt != null && System.currentTimeMillis() > inner.expiresAt) {
            throw ZvxExpiredException()
        }

        val configKey: ByteArray = if (inner.mode == 1) {
            if (password.isNullOrEmpty()) throw ZvxPasswordRequiredException()
            val kdf = inner.kdf ?: throw ZvxTamperException()
            argon2(password, Base64.decode(kdf.salt))
        } else {
            openKey
        }
        return try {
            val plain = aesGcmDecrypt(
                configKey,
                Base64.decode(inner.cfgNonce),
                CFG_AAD.toByteArray(),
                Base64.decode(inner.cfgCipher),
            )
            String(plain, Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            if (inner.mode == 1) throw ZvxWrongPasswordException() else throw ZvxTamperException()
        }
    }

    private fun readInner(bytes: ByteArray): ZvxInner {
        if (bytes.size < MAGIC.size + 1) throw ZvxTamperException()
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw ZvxTamperException()
        when (bytes[MAGIC.size]) {
            VERSION -> Unit
            LEGACY_VERSION -> throw ZvxLegacyException()
            else -> throw ZvxVersionException()
        }
        if (bytes.size < MAGIC.size + 1 + NONCE_LEN + 16) throw ZvxTamperException()
        val nonceStart = MAGIC.size + 1
        val outerNonce = bytes.copyOfRange(nonceStart, nonceStart + NONCE_LEN)
        val outerCipher = bytes.copyOfRange(nonceStart + NONCE_LEN, bytes.size)
        val innerBytes = try {
            aesGcmDecrypt(openKey, outerNonce, aad(), outerCipher)
        } catch (e: AEADBadTagException) {
            throw ZvxTamperException()
        }
        return try {
            json.decodeFromString(ZvxInner.serializer(), String(innerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ZvxTamperException()
        }
    }

    private fun aad(): ByteArray = MAGIC + byteArrayOf(VERSION)

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(cipherText)
    }

    private fun argon2(password: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(ARGON_MEM_KIB)
            .withIterations(ARGON_ITERATIONS)
            .withParallelism(ARGON_PARALLELISM)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(KEY_LEN)
        generator.generateBytes(password.toCharArray(), out)
        return out
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }
}

@Serializable
private data class ZvxInner(
    val mode: Int,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val kdf: ZvxKdf? = null,
    val cfgNonce: String,
    val cfgCipher: String,
)

@Serializable
private data class ZvxKdf(
    val salt: String,
    val memKiB: Int,
    val iterations: Int,
    val parallelism: Int,
)
