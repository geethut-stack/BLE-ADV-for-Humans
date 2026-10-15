package com.lampremote.core

import org.junit.Assert.assertTrue
import org.junit.Test

class CoreVectorsTest {
    @Test fun allVectorsMatchPythonCodecs() {
        val fails = CoreChecks.run()
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }
}
