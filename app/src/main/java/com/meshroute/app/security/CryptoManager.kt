package com.meshroute.app.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Handles AES-256-GCM authenticated encryption and decryption for emergency payloads.
 * Relays handle and carry only ciphertext (Base64 string of IV + Ciphertext + Tag).
 */
object CryptoManager {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Output format: Base64 string of [12-byte IV + ciphertext + 16-byte GCM tag].
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherBytes = cipher.doFinal(plaintext)
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return encodeBase64(combined)
    }

    /**
     * Encrypts a plaintext UTF-8 string into Base64 ciphertext.
     */
    fun encryptString(plaintext: String, key: SecretKey): String {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8), key)
    }

    /**
     * Decrypts a Base64 string containing [12-byte IV + ciphertext + 16-byte GCM tag].
     * Returns the decrypted plaintext bytes, or throws IllegalArgumentException/GeneralSecurityException on tampering.
     */
    fun decrypt(encryptedBase64: String, key: SecretKey): ByteArray {
        val combined = decodeBase64(encryptedBase64)
        if (combined.size < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw IllegalArgumentException("Ciphertext too short to contain valid IV and GCM authentication tag")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherBytesLength = combined.size - GCM_IV_LENGTH_BYTES
        val cipherBytes = ByteArray(cipherBytesLength)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytesLength)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(cipherBytes)
    }

    /**
     * Decrypts Base64 ciphertext back into a plaintext UTF-8 string.
     */
    fun decryptString(encryptedBase64: String, key: SecretKey): String {
        return decrypt(encryptedBase64, key).toString(Charsets.UTF_8)
    }

    /** Helper for robust cross-platform Base64 encoding */
    fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Helper for robust cross-platform Base64 decoding */
    fun decodeBase64(str: String): ByteArray {
        return Base64.getDecoder().decode(str.trim())
    }
}
