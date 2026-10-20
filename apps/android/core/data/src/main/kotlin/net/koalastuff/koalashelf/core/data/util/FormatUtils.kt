package net.koalastuff.koalashelf.core.data.util

import net.koalastuff.koalashelf.core.data.i18n.DeKoalaStrings
import net.koalastuff.koalashelf.core.data.i18n.KoalaStrings
import net.koalastuff.koalashelf.core.network.Chapter
import net.koalastuff.koalashelf.core.network.LibraryItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 3725 -> "1:02:05", 125 -> "2:05" (matching `clock()` in `apps/web/src/lib/format.ts`).
 */
fun formatClock(seconds: Double): String {
    val s = max(0L, floor(seconds).toLong())
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = (s % 60L).toString().padStart(2, '0')
    return if (h > 0L) {
        "$h:${m.toString().padStart(2, '0')}:$sec"
    } else {
        "$m:$sec"
    }
}

/**
 * "1:02:05", "2:05" or "125" -> seconds; null when invalid (`parseClock()` in `format.ts`).
 */
fun parseClock(text: String?): Double? {
    if (text.isNullOrBlank()) return null
    val trimmed = text.trim()
    if (!Regex("^\\d+(:\\d{1,2}){0,2}$").matches(trimmed)) return null
    var acc = 0L
    for (part in trimmed.split(':')) {
        acc = acc * 60L + (part.toLongOrNull() ?: return null)
    }
    return acc.toDouble()
}

/**
 * 3725 -> "1 h 2 min" / "1 Std. 2 Min." (`duration()` in `format.ts`).
 */
fun formatDuration(seconds: Double, strings: KoalaStrings = DeKoalaStrings): String {
    val totalMinutes = (seconds / 60.0).roundToLong()
    val h = totalMinutes / 60L
    val m = totalMinutes % 60L
    if (h == 0L) return "${max(1L, m)} ${strings.unitMinutes}"
    return if (m > 0L) {
        "$h ${strings.unitHours} $m ${strings.unitMinutes}"
    } else {
        "$h ${strings.unitHours}"
    }
}

/**
 * Formats byte count with decimal SI units ("142.5 MB") matching `bytes()` in `format.ts`.
 */
fun formatBytes(bytes: Long, isGerman: Boolean = true): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unitIdx = 0
    while (value >= 1000.0 && unitIdx < units.lastIndex) {
        value /= 1000.0
        unitIdx++
    }
    val locale = if (isGerman) Locale.GERMAN else Locale.US
    val formatted = if (value % 1.0 == 0.0 || unitIdx == 0) {
        value.roundToLong().toString()
    } else {
        String.format(locale, "%.1f", value)
    }
    return "$formatted ${units[unitIdx]}"
}

/**
 * Formats playback speed ("1.2×" / "1,25×") matching `speed()` in `format.ts`.
 */
fun formatSpeed(rate: Float, isGerman: Boolean = false): String {
    val rounded = (rate * 100f).roundToInt() / 100f
    val pattern = if ((rounded * 100f).roundToInt() % 10 != 0) "%.2f" else "%.1f"
    val locale = if (isGerman) Locale.GERMAN else Locale.US
    return "${String.format(locale, pattern, rounded)}×"
}

/**
 * Clamps playback speed between 0.5x and 3.5x on a 0.05 step grid (`clampRate` in `player.svelte.ts`).
 */
fun clampRate(rate: Float): Float {
    val clamped = rate.coerceIn(0.5f, 3.5f)
    return (clamped * 20f).roundToInt() / 20f
}

/**
 * Converts HTML audiobook descriptions to clean plain text with paragraph breaks (`htmlToText` in `format.ts`).
 */
fun htmlToText(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(p|div|li|h[1-6])>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

/**
 * Smart rewind seconds after a pause of `pausedMs` (`smartRewindSeconds` in `prefs.svelte.ts`).
 */
fun smartRewindSeconds(pausedMs: Long): Int = when {
    pausedMs > 60 * 60_000L -> 30
    pausedMs > 10 * 60_000L -> 15
    pausedMs > 60_000L -> 5
    else -> 0
}

/**
 * Checks if current time (in minutes since midnight) falls inside the night window (`inNightWindow` in `prefs.svelte.ts`).
 */
fun inNightWindow(minutesSinceMidnight: Int, fromMinutes: Int, toMinutes: Int): Boolean {
    return if (fromMinutes <= toMinutes) {
        minutesSinceMidnight in fromMinutes until toMinutes
    } else {
        minutesSinceMidnight >= fromMinutes || minutesSinceMidnight < toMinutes
    }
}

data class SleepForecast(
    val seconds: Double,
    val chapterIndex: Int?,
)

/**
 * Smart sleep timer forecast (`forecast` in `apps/web/src/lib/player/sleep.ts`):
 * Finds the first chapter end reached between `minLeftSeconds` and `maxLeftSeconds` of listening.
 */
fun forecastSleepWindow(
    chapters: List<Chapter>,
    currentTime: Double,
    rate: Float,
    minLeftSeconds: Double,
    maxLeftSeconds: Double,
): SleepForecast {
    val safeRate = rate.coerceAtLeast(0.5f).toDouble()
    for (i in chapters.indices) {
        val until = (chapters[i].end - currentTime) / safeRate
        if (until < 0.0) continue
        if (until > maxLeftSeconds) break
        if (until >= minLeftSeconds) {
            return SleepForecast(seconds = until, chapterIndex = i)
        }
    }
    return SleepForecast(seconds = max(0.0, maxLeftSeconds), chapterIndex = null)
}

/**
 * Clamps a smart sleep timer window (`clampWindow` in `sleep.ts`).
 */
fun clampSleepWindow(minMinutes: Int, maxMinutes: Int, changedMin: Boolean): Pair<Int, Int> {
    val step = 5
    val loBound = 5
    val hiBound = 180
    var minVal = minMinutes.coerceIn(loBound, hiBound - step)
    var maxVal = maxMinutes.coerceIn(loBound + step, hiBound)
    if (maxVal - minVal < step) {
        if (changedMin) {
            maxVal = minVal + step
        } else {
            minVal = maxVal - step
        }
    }
    return minVal to maxVal
}

/**
 * Extracts a book's sequence number within `seriesName` (`sequenceIn` in `series-order.ts`).
 */
fun sequenceIn(item: LibraryItem, seriesName: String): Double? {
    val meta = item.media.metadata
    val direct = meta.parsedSeriesList.find { it.name.equals(seriesName, ignoreCase = true) }?.sequence
    val raw = direct ?: meta.seriesName
        ?.split(", ")
        ?.find { part ->
            part.equals(seriesName, ignoreCase = true) ||
                part.startsWith("$seriesName #", ignoreCase = true)
        }
        ?.substring(seriesName.length)
        ?.removePrefix(" #")
        ?.trim()
    val parsed = raw?.replace(',', '.')?.toDoubleOrNull()
    return parsed?.takeIf { it.isFinite() }
}

/**
 * Sorts books of a series in reading order (`inSeriesOrder` in `series-order.ts`).
 */
fun inSeriesOrder(items: List<LibraryItem>, seriesName: String): List<LibraryItem> {
    return items
        .map { item -> item to sequenceIn(item, seriesName) }
        .sortedWith { a, b ->
            val seqA = a.second
            val seqB = b.second
            when {
                seqA != null && seqB != null && seqA != seqB -> seqA.compareTo(seqB)
                seqA == null && seqB != null -> 1
                seqA != null && seqB == null -> -1
                else -> naturalCompare(a.first.title, b.first.title)
            }
        }
        .map { it.first }
}

private val chunkRegex = Regex("(\\d+|\\D+)")

fun naturalCompare(a: String, b: String): Int {
    val chunksA = chunkRegex.findAll(a.lowercase()).map { it.value }.toList()
    val chunksB = chunkRegex.findAll(b.lowercase()).map { it.value }.toList()
    val len = min(chunksA.size, chunksB.size)
    for (i in 0 until len) {
        val ca = chunksA[i]
        val cb = chunksB[i]
        val na = ca.toBigIntegerOrNull()
        val nb = cb.toBigIntegerOrNull()
        val cmp = if (na != null && nb != null) {
            na.compareTo(nb)
        } else {
            ca.compareTo(cb)
        }
        if (cmp != 0) return cmp
    }
    return chunksA.size.compareTo(chunksB.size)
}

data class DurationFilter(
    val minSeconds: Double? = null,
    val maxSeconds: Double? = null,
    val cleanQuery: String,
)

/**
 * Parses natural duration expressions like "unter 8 stunden", "<4h", ">10h", "4-8h"
 * from search queries (`parseDurationQuery` in `apps/web/src/lib/library-query.ts`).
 */
fun parseDurationQuery(raw: String): DurationFilter {
    var text = raw.trim()
    var minHours: Double? = null
    var maxHours: Double? = null

    val rangeRegex = Regex(
        "(?:^|\\s)(\\d+(?:[.,]\\d+)?)\\s*(?:-|–|bis|to)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:h|std|stunden|hours|hrs)?(?:\\s|$)",
        RegexOption.IGNORE_CASE,
    )
    rangeRegex.find(text)?.let { m ->
        minHours = m.groupValues[1].replace(',', '.').toDoubleOrNull()
        maxHours = m.groupValues[2].replace(',', '.').toDoubleOrNull()
        text = text.replaceRange(m.range, " ").trim()
    }

    val underRegex = Regex(
        "(?:^|\\s)(?:(?:nur|only)\\s+)?(?:<|unter|under|weniger\\s+als|less\\s+than)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:h|std|stunden|hours|hrs)?(?:\\s|$)",
        RegexOption.IGNORE_CASE,
    )
    underRegex.find(text)?.let { m ->
        maxHours = m.groupValues[1].replace(',', '.').toDoubleOrNull()
        text = text.replaceRange(m.range, " ").trim()
    }

    val overRegex = Regex(
        "(?:^|\\s)(?:(?:nur|only)\\s+)?(?:>|über|ueber|over|mehr\\s+als|more\\s+than)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:h|std|stunden|hours|hrs)?(?:\\s|$)",
        RegexOption.IGNORE_CASE,
    )
    overRegex.find(text)?.let { m ->
        minHours = m.groupValues[1].replace(',', '.').toDoubleOrNull()
        text = text.replaceRange(m.range, " ").trim()
    }

    return DurationFilter(
        minSeconds = minHours?.let { it * 3600.0 },
        maxSeconds = maxHours?.let { it * 3600.0 },
        cleanQuery = text.replace(Regex("\\s+"), " ").trim(),
    )
}

/**
 * Defuses spreadsheet formula injection on CSV export (`export.ts`).
 */
fun defuseCsvCell(value: String): String {
    val safe = if (value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
        "'$value"
    } else {
        value
    }
    return if (safe.contains(',') || safe.contains('"') || safe.contains('\n')) {
        "\"${safe.replace("\"", "\"\"")}\""
    } else {
        safe
    }
}

fun formatLocalDay(epochMillis: Long): String {
    val dt = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return dt.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

fun formatEnglishWeekday(epochMillis: Long): String {
    val dt = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return dt.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
}

fun formatEndsAtClock(nowMillis: Long, remainingRealSeconds: Double): String {
    val endInstant = java.time.Instant.ofEpochMilli(nowMillis + (remainingRealSeconds.coerceAtLeast(0.0) * 1000.0).roundToLong())
    val localTime = endInstant.atZone(ZoneId.systemDefault()).toLocalTime()
    return localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
}

/**
 * Formats relative time ("just now", "2 h ago" / "vor 2 Std.") matching `relativeTime()` in `format.ts`.
 */
fun formatRelativeTime(epochMillis: Long, isGerman: Boolean = true): String {
    if (epochMillis <= 0L) return ""
    val diffSec = max(0L, (System.currentTimeMillis() - epochMillis) / 1000L)
    return when {
        diffSec < 60L -> if (isGerman) "gerade eben" else "just now"
        diffSec < 3600L -> {
            val m = diffSec / 60L
            if (isGerman) "vor $m Min." else "$m min ago"
        }
        diffSec < 86400L -> {
            val h = diffSec / 3600L
            if (isGerman) "vor $h Std." else "$h h ago"
        }
        diffSec < 86400L * 30L -> {
            val d = diffSec / 86400L
            if (isGerman) "vor $d ${if (d == 1L) "Tag" else "Tagen"}" else "$d d ago"
        }
        else -> formatLocalDay(epochMillis)
    }
}

/**
 * Parses a User-Agent string into a concise browser + OS label (`describeUserAgent` in `apps/web/src/lib/device.ts`).
 */
fun describeUserAgent(ua: String?): String {
    if (ua.isNullOrBlank()) return ""
    if (ua.contains("KoalaShelf", ignoreCase = true) && ua.contains("Android", ignoreCase = true)) {
        return "KoalaShelf Android"
    }
    val browser = when {
        Regex("Edg/").containsMatchIn(ua) -> "Edge"
        Regex("OPR/|Opera").containsMatchIn(ua) -> "Opera"
        Regex("Firefox/|FxiOS/").containsMatchIn(ua) -> "Firefox"
        Regex("CriOS/|Chrome/").containsMatchIn(ua) && !Regex("Chromium").containsMatchIn(ua) -> "Chrome"
        Regex("Safari/").containsMatchIn(ua) && !Regex("Chrome|Chromium").containsMatchIn(ua) -> "Safari"
        else -> ""
    }
    val os = when {
        Regex("iPhone").containsMatchIn(ua) -> "iPhone"
        Regex("iPad").containsMatchIn(ua) -> "iPad"
        Regex("Android").containsMatchIn(ua) -> "Android"
        Regex("Mac OS X|Macintosh").containsMatchIn(ua) -> "macOS"
        Regex("Windows").containsMatchIn(ua) -> "Windows"
        Regex("CrOS").containsMatchIn(ua) -> "ChromeOS"
        Regex("Linux").containsMatchIn(ua) -> "Linux"
        else -> ""
    }
    if (browser.isNotEmpty() && os.isNotEmpty()) return "$browser · $os"
    return browser.ifEmpty { os.ifEmpty { ua.take(48) } }
}
