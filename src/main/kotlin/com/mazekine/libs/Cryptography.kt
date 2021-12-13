package com.mazekine.libs

import org.bukkit.Bukkit
import java.nio.ByteBuffer
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A Kotlin adaptation of the library for ChaCha20 / Poly1305 encryption/decryption
 *
 * @author Mkyong, https://mkyong.com/java/java-11-chacha20-poly1305-encryption-examples/
 */
class ChaCha20Poly1305 {
    private val salt by lazy {
        try {
            Bukkit.getPluginManager()
                .getPlugin("EverCraft")
                ?.config
                ?.get("security.salt")
                ?: "EverCraft"
        } catch (e: Exception) {
            "EverCraft"
        }
    } //"I am a super-duper secret salt message"

    /**
     * Encrypt a byte array using the secret key
     *
     * @param pText Data to encrypt
     * @param key   Secret key
     * @param nonce (Random) nonce
     * @return  Encoded byte array
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun encrypt(
        pText: ByteArray?,
        key: SecretKey?,
        nonce: ByteArray? = Companion.nonce // if no nonce, generate a random 12 bytes nonce
    ): ByteArray {
        val cipher = Cipher.getInstance(ENCRYPT_ALGO)

        // IV, initialization value with nonce
        val iv = IvParameterSpec(nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encryptedText = cipher.doFinal(pText)

        // append nonce to the encrypted text
        return ByteBuffer.allocate(encryptedText.size + NONCE_LEN)
            .put(encryptedText)
            .put(nonce)
            .array()
    }

    fun encryptStringWithPassword (
        data: String? = "",
        password: String,
        nonce: ByteArray? = Companion.nonce
    ): String {
        return encrypt(
            data?.toByteArray(),
            secretKeyFromString(password),
            nonce
        ).toHex()
    }

    @Throws(Exception::class)
    fun decrypt(cText: ByteArray, key: SecretKey?): ByteArray {
        val bb = ByteBuffer.wrap(cText)

        // split cText to get the appended nonce
        val encryptedText = ByteArray(cText.size - NONCE_LEN)
        val nonce = ByteArray(NONCE_LEN)
        bb[encryptedText]
        bb[nonce]
        val cipher = Cipher.getInstance(ENCRYPT_ALGO)
        val iv = IvParameterSpec(nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)

        // decrypted text
        return cipher.doFinal(encryptedText)
    }

    fun decryptStringWithPassword(
        data: String,
        password: String
    ): String {
        return String(decrypt(
            data.hexToByteArray(),
            secretKeyFromString(password)
        ))
    }

    /**
     * Generates a secret key from a password
     * Uses a concept proposed by Baeldung: https://www.baeldung.com/java-secret-key-to-string
     *
     * @param input  User's arbitrary string
     * @return SecretKey
     * @throws NoSuchAlgorithmException In case the library doesn't support ChaCha20
     */
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun secretKeyFromString(input: String): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(input.toCharArray(), salt.toString().toByteArray(), 65536, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    companion object {
        private const val ENCRYPT_ALGO = "ChaCha20-Poly1305"
        private const val NONCE_LEN = 12 // 96 bits, 12 bytes

        // 96-bit nonce (12 bytes)
        private val nonce: ByteArray
            get() {
                val newNonce = ByteArray(NONCE_LEN)
                SecureRandom().nextBytes(newNonce)
                return newNonce
            }
    }
}

/**
 * Converts a byte array to a hex view
 *
 * @return String
 */
fun ByteArray.toHex(): String {
    val result = StringBuilder()
    for (temp in this) {
        result.append(String.format("%02x", temp))
    }
    return result.toString()
}

fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { PluginLocale.getLocalizedError("error.hex.odd", colored = false) }

    return ByteArray(length / 2) {
        Integer.parseInt(this, it * 2, (it + 1) * 2, 16).toByte()
    }
}

