package com.meshroute.app.security

import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Manages standard emergency mesh encryption keys for the SOS pipeline.
 */
object KeyManager {
    /**
     * Standard pre-shared mesh emergency passphrase for hackathon MVP/demo emergency broadcasts.
     * In a multi-tenant deployment, this can be derived from team/emergency channel passphrase.
     */
    private const val DEFAULT_EMERGENCY_PASSPHRASE = "MeshRoute-Emergency-Broadcast-Key-2026"

    val defaultEmergencyKey: SecretKey by lazy {
        deriveKey(DEFAULT_EMERGENCY_PASSPHRASE)
    }

    /**
     * Derives a 256-bit AES SecretKey from any passphrase using SHA-256.
     */
    fun deriveKey(passphrase: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
