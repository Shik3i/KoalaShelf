package net.koalastuff.koalashelf.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoalaAccentCalculatorTest {

    @Test
    fun `isHex and parseHexColor validate 6 digit hex codes`() {
        assertTrue(KoalaAccentCalculator.isHex("#e08a3c"))
        assertNotNull(KoalaAccentCalculator.parseHexColor("#e08a3c"))
        assertEquals("#E08A3C", KoalaAccentCalculator.colorToHex(KoalaAccentCalculator.parseHexColor("#e08a3c")!!))
        assertFalse(KoalaAccentCalculator.isHex("not-a-hex"))
        assertNull(KoalaAccentCalculator.parseHexColor("#12345"))
    }

    @Test
    fun `deriveCustomAccent enforces WCAG 4_5 contrast in light and dark mode`() {
        val testHexes = listOf("#E08A3C", "#2563EB", "#10B981", "#F43F5E", "#FAF6F0", "#111111")
        for (hex in testHexes) {
            val derived = KoalaAccentCalculator.deriveCustomAccent(hex)
            val lightContrast = KoalaAccentCalculator.contrast(derived.light.accent, LightKoalaColors.bg)
            val darkContrast = KoalaAccentCalculator.contrast(derived.dark.accent, DarkKoalaColors.bg)

            assertTrue("Light contrast ($lightContrast) should be >= 4.5 for $hex", lightContrast >= 4.5)
            assertTrue("Dark contrast ($darkContrast) should be >= 4.5 for $hex", darkContrast >= 4.5)
        }
    }
}
