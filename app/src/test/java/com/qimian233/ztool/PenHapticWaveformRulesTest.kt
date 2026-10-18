package com.qimian233.ztool

import com.qimian233.ztool.data.pen.PenHapticWaveformIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenHapticWaveformRulesTest {

    @Test
    fun `parses ordered rules`() {
        val rules = PenHapticWaveformIds.parseRules("36:33,34:33,7:1")
        assertEquals(listOf(36, 34, 7), rules.keys.toList())
        assertEquals(33, rules[36])
        assertEquals(33, rules[34])
        assertEquals(1, rules[7])
    }

    @Test
    fun `drops invalid, self and cross-family rules`() {
        val rules = PenHapticWaveformIds.parseRules("36:36,36:7,99:33,0:1,abc,33:34")
        assertEquals(mapOf(33 to 34), rules)
    }

    @Test
    fun `keeps the first rule for a repeated source`() {
        val rules = PenHapticWaveformIds.parseRules("36:33,36:34")
        assertEquals(33, rules[36])
    }

    @Test
    fun `format and parse round trip`() {
        val rules = PenHapticWaveformIds.parseRules("36:33,34:33")
        val text = PenHapticWaveformIds.formatRules(rules)
        assertEquals("36:33,34:33", text)
        assertEquals(rules, PenHapticWaveformIds.parseRules(text))
    }

    @Test
    fun `sanitize handles an in-memory map`() {
        val sanitized = PenHapticWaveformIds.sanitizeRules(
            linkedMapOf(36 to 33, 33 to 33, 34 to 7)
        )
        assertEquals(mapOf(36 to 33), sanitized)
    }

    @Test
    fun `empty input yields no rules`() {
        assertTrue(PenHapticWaveformIds.parseRules("").isEmpty())
        assertTrue(PenHapticWaveformIds.parseRules("  ").isEmpty())
    }
}
