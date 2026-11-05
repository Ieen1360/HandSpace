package com.handspace.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArSupportTest {

    @Test
    fun onlySupportedIsReady() {
        ArSupport.values().forEach { support ->
            assertEquals("isReady de $support", support == ArSupport.SUPPORTED, support.isReady)
        }
        assertTrue(ArSupport.SUPPORTED.isReady)
        assertFalse(ArSupport.UNKNOWN_CHECKING.isReady)
        assertFalse(ArSupport.UNKNOWN_ERROR.isReady)
        assertFalse(ArSupport.UNKNOWN_TIMED_OUT.isReady)
    }
}
