package com.zvpn.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZvxFileNameTest {
    @Test
    fun `names become safe file names ending in zsx`() {
        assertEquals("Berlin_Fast.zvx", zvxFileName("Berlin Fast"))
        assertEquals("config.zvx", zvxFileName("  "))
        assertEquals("node.zvx", zvxFileName("node.zvx"))
        assertEquals("_____.zvx", zvxFileName("برلین"))
    }

    @Test
    fun `long names are cut before the extension`() {
        val name = zvxFileName("x".repeat(80))
        assertTrue(name.endsWith(".zvx"))
        assertEquals(48 + 4, name.length)
    }
}
