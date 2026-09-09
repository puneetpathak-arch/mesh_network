package com.meshroute.app.security

import org.junit.Assert.*
import org.junit.Test

class NoiseTransportSecurityTest {

    @Test
    fun testNoiseXXHandshakeFlow() {
        val initiator = NoiseTransportSecurity("Node_Initiator")
        val responder = NoiseTransportSecurity("Node_Responder")

        // Step 1: Initiator -> Responder (Message 1)
        val msg1 = initiator.initiateHandshake()
        assertEquals(1, msg1.step)
        assertEquals(NoiseHandshakeState.EXPECTING_RESPONSE, initiator.handshakeState)

        // Step 2: Responder -> Initiator (Message 2)
        val msg2 = responder.respondHandshake(msg1)
        assertEquals(2, msg2.step)
        assertEquals(NoiseHandshakeState.EXPECTING_FINAL, responder.handshakeState)

        // Step 3: Initiator finalizes (Message 3)
        val success = initiator.finalizeHandshake(msg2)
        assertTrue(success)
        assertEquals(NoiseHandshakeState.ESTABLISHED, initiator.handshakeState)

        responder.completeResponderState()
        assertEquals(NoiseHandshakeState.ESTABLISHED, responder.handshakeState)

        // Verify shared session key derivation
        assertNotNull(initiator.sharedSessionKey)
        assertNotNull(responder.sharedSessionKey)
    }

    @Test
    fun testNoiseSessionPayloadEncryptionAndDecryption() {
        val initiator = NoiseTransportSecurity("Node_Initiator")
        val responder = NoiseTransportSecurity("Node_Responder")

        val msg1 = initiator.initiateHandshake()
        val msg2 = responder.respondHandshake(msg1)
        initiator.finalizeHandshake(msg2)

        val sampleText = "SOS Emergency distress packet over BLE session"
        val ciphertext = initiator.encryptSessionPayload(sampleText.toByteArray(Charsets.UTF_8))

        assertNotNull(ciphertext)
        assertTrue(ciphertext.size > sampleText.length)

        val decryptedBytes = responder.decryptSessionPayload(ciphertext)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(sampleText, decryptedText)
    }
}
