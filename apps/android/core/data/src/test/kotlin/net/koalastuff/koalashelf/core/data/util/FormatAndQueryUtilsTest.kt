package net.koalastuff.koalashelf.core.data.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.koalastuff.koalashelf.core.network.BookMedia
import net.koalastuff.koalashelf.core.network.BookMetadata
import net.koalastuff.koalashelf.core.network.Chapter
import net.koalastuff.koalashelf.core.network.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndQueryUtilsTest {

    @Test
    fun `formatClock and parseClock roundtrip MM_SS and HH_MM_SS`() {
        assertEquals("0:05", formatClock(5.0))
        assertEquals("12:34", formatClock(12 * 60 + 34.0))
        assertEquals("1:02:03", formatClock(3600 + 2 * 60 + 3.0))

        assertEquals(754.0, parseClock("12:34")!!, 0.01)
        assertEquals(3723.0, parseClock("01:02:03")!!, 0.01)
        assertNull(parseClock("invalid"))
    }

    @Test
    fun `smartRewindSeconds scales with pause duration`() {
        assertEquals(0, smartRewindSeconds(10_000L))
        assertEquals(5, smartRewindSeconds(5 * 60_000L))
        assertEquals(15, smartRewindSeconds(30 * 60_000L))
        assertEquals(30, smartRewindSeconds(3 * 3_600_000L))
    }

    @Test
    fun `inNightWindow handles overnight and same-day windows`() {
        // 22:00 (1320) -> 06:00 (360) overnight
        assertTrue(inNightWindow(minutesSinceMidnight = 23 * 60, fromMinutes = 22 * 60, toMinutes = 6 * 60))
        assertTrue(inNightWindow(minutesSinceMidnight = 2 * 60, fromMinutes = 22 * 60, toMinutes = 6 * 60))
        assertFalse(inNightWindow(minutesSinceMidnight = 14 * 60, fromMinutes = 22 * 60, toMinutes = 6 * 60))
    }

    @Test
    fun `forecastSleepWindow resolves chapter end within window or falls back`() {
        val chapters = listOf(
            Chapter(id = 0, start = 0.0, end = 600.0, title = "Kapitel 1"),
            Chapter(id = 1, start = 600.0, end = 1800.0, title = "Kapitel 2"),
        )
        // 5 minutes remaining in Chapter 1, window 2m..20m -> ends at Chapter 1 (index 0)
        val within = forecastSleepWindow(
            chapters = chapters,
            currentTime = 300.0,
            rate = 1.0f,
            minLeftSeconds = 120.0,
            maxLeftSeconds = 1200.0,
        )
        assertEquals(0, within.chapterIndex)
        assertEquals(300.0, within.seconds, 0.01)

        // In Chapter 2 at 600s (20m left), window 5m..10m -> no chapter end in window, falls back to maxLeftSeconds
        val fallback = forecastSleepWindow(
            chapters = chapters,
            currentTime = 600.0,
            rate = 1.0f,
            minLeftSeconds = 300.0,
            maxLeftSeconds = 600.0,
        )
        assertNull(fallback.chapterIndex)
        assertEquals(600.0, fallback.seconds, 0.01)
    }

    @Test
    fun `parseDurationQuery parses ranges, upper bounds, lower bounds and strips expression`() {
        val r1 = parseDurationQuery("thriller < 6h")
        assertEquals(6 * 3600.0, r1.maxSeconds!!, 0.01)
        assertEquals("thriller", r1.cleanQuery)

        val r2 = parseDurationQuery("5-10h fantasy")
        assertEquals(5 * 3600.0, r2.minSeconds!!, 0.01)
        assertEquals(10 * 3600.0, r2.maxSeconds!!, 0.01)
        assertEquals("fantasy", r2.cleanQuery)

        val r3 = parseDurationQuery("> 12 stunden")
        assertEquals(12 * 3600.0, r3.minSeconds!!, 0.01)
    }

    @Test
    fun `inSeriesOrder sorts decimal and integer sequence numbers before unnumbered items`() {
        fun item(id: String, title: String, seq: String?) = LibraryItem(
            id = id,
            media = BookMedia(
                metadata = BookMetadata(
                    title = title,
                    series = if (seq != null) {
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive("s1"),
                                        "name" to JsonPrimitive("Dune"),
                                        "sequence" to JsonPrimitive(seq),
                                    ),
                                ),
                            ),
                        )
                    } else null,
                ),
            ),
        )
        val books = listOf(
            item("b10", "Book 10", "10"),
            item("b2", "Book 2", "2"),
            item("b1_5", "Book 1.5", "1.5"),
            item("b1", "Book 1", "1"),
            item("bx", "Appendix", null),
        )
        val sorted = inSeriesOrder(books, seriesName = "Dune")
        assertEquals(listOf("b1", "b1_5", "b2", "b10", "bx"), sorted.map { it.id })
    }

    @Test
    fun `defuseCsvCell neutralizes spreadsheet formula injection`() {
        assertEquals("'=SUM(A1:A2)", defuseCsvCell("=SUM(A1:A2)"))
        assertEquals("Normal note", defuseCsvCell("Normal note"))
    }
}
