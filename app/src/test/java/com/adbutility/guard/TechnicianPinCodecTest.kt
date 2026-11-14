package com.adbutility.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de hashing con salt para el PIN del Modo Técnico.
 */
class TechnicianPinCodecTest {

    @Test
    fun testPinHashAndVerify() {
        val salt1 = TechnicianPinCodec.generateSaltHex()
        val salt2 = TechnicianPinCodec.generateSaltHex()
        assertNotEquals(salt1, salt2)

        val hash = TechnicianPinCodec.hashPin("4826", salt1)
        assertNotEquals("4826", hash)
        assertTrue(TechnicianPinCodec.verify("4826", salt1, hash))
        assertFalse(TechnicianPinCodec.verify("0000", salt1, hash))
        assertFalse(TechnicianPinCodec.verify("", salt1, hash))
        assertFalse(TechnicianPinCodec.verify("4826", salt2, hash))
    }
}

