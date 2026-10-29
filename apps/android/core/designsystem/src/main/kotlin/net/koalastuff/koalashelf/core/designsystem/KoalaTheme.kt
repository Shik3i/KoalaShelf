package net.koalastuff.koalashelf.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class KoalaThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String): KoalaThemeMode = entries.find { it.id == id } ?: SYSTEM
    }
}

enum class KoalaAccentPreset(
    val id: String,
    val lightAccent: Color,
    val lightOnAccent: Color,
    val darkAccent: Color,
    val darkOnAccent: Color,
) {
    VINYL("vinyl", Color(0xFFB8391E), Color(0xFFFFFFFF), Color(0xFFE8A33D), Color(0xFF1A1206)),
    OCEAN("ocean", Color(0xFF1D63B8), Color(0xFFFFFFFF), Color(0xFF7DB8F5), Color(0xFF0A1D33)),
    TEAL("teal", Color(0xFF0B7268), Color(0xFFFFFFFF), Color(0xFF4FC7B5), Color(0xFF06201C)),
    FOREST("forest", Color(0xFF2A7230), Color(0xFFFFFFFF), Color(0xFF86C77A), Color(0xFF0E220C)),
    INDIGO("indigo", Color(0xFF4A4FC4), Color(0xFFFFFFFF), Color(0xFF9EA3FF), Color(0xFF12133A)),
    PLUM("plum", Color(0xFF9B3A82), Color(0xFFFFFFFF), Color(0xFFE59AD0), Color(0xFF2A0F22)),
    CUSTOM("custom", Color(0xFFB8391E), Color(0xFFFFFFFF), Color(0xFFE8A33D), Color(0xFF1A1206));

    companion object {
        fun fromId(id: String): KoalaAccentPreset = entries.find { it.id == id } ?: VINYL
    }
}

enum class KoalaFontChoice(val id: String) {
    STANDARD("standard"),
    LITERARY("literary"),
    VINTAGE("vintage"),
    READABLE("readable"),
    DYSLEXIC("dyslexic"),
    MONO("mono"),
    SYSTEM("system");

    fun displayFontFamily(): FontFamily = when (this) {
        STANDARD -> FontFamily(Font(R.font.gloock_regular, FontWeight.Normal))
        LITERARY -> FontFamily(Font(R.font.literata_regular, FontWeight.Normal))
        VINTAGE -> FontFamily(Font(R.font.fraunces_regular, FontWeight.Normal))
        READABLE -> FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal))
        DYSLEXIC -> FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))
        MONO -> FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))
        SYSTEM -> FontFamily.Default
    }

    fun bodyFontFamily(): FontFamily = when (this) {
        STANDARD -> FontFamily(Font(R.font.manrope_regular, FontWeight.Normal))
        LITERARY -> FontFamily(Font(R.font.literata_regular, FontWeight.Normal))
        VINTAGE -> FontFamily(Font(R.font.fraunces_regular, FontWeight.Normal))
        READABLE -> FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal))
        DYSLEXIC -> FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))
        MONO -> FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))
        SYSTEM -> FontFamily.Default
    }

    companion object {
        fun fromId(id: String): KoalaFontChoice = entries.find { it.id == id } ?: STANDARD
    }
}

enum class KoalaTextSize(val id: String, val scale: Float) {
    SM("sm", 0.9375f),
    MD("md", 1.0f),
    LG("lg", 1.125f),
    XL("xl", 1.25f);

    companion object {
        fun fromId(id: String): KoalaTextSize = entries.find { it.id == id } ?: MD
    }
}

enum class KoalaMotionMode(val id: String) {
    SYSTEM("system"),
    REDUCE("reduce"),
    FULL("full");

    companion object {
        fun fromId(id: String): KoalaMotionMode = entries.find { it.id == id } ?: SYSTEM
    }
}

@Immutable
data class CustomAccentPair(
    val accent: Color,
    val onAccent: Color,
    val adjusted: Boolean,
)

@Immutable
data class DerivedCustomAccent(
    val light: CustomAccentPair,
    val dark: CustomAccentPair,
    val adjusted: Boolean,
)

/**
 * 1:1 port of `apps/web/src/lib/accent.ts`:
 * Adjusts any user-picked hex color in OKLCH lightness so that it meets WCAG 4.5:1
 * contrast against all surfaces (`bg`, `card`, `panel`, `sel`, `curBg`) in both themes.
 */
object KoalaAccentCalculator {
    private const val MIN_CONTRAST = 4.5

    fun isHex(s: String): Boolean = Regex("^#[0-9a-fA-F]{6}$").matches(s.trim())

    fun parseHexColor(hex: String): Color? {
        val cleaned = hex.trim()
        if (!isHex(cleaned)) return null
        val rgb = cleaned.substring(1).toLong(16)
        return Color(0xFF000000 or rgb)
    }

    fun colorToHex(color: Color): String {
        val r = (color.red.coerceIn(0f, 1f) * 255f).roundToInt()
        val g = (color.green.coerceIn(0f, 1f) * 255f).roundToInt()
        val b = (color.blue.coerceIn(0f, 1f) * 255f).roundToInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun toLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun toGamma(c: Double): Double =
        if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

    fun luminance(color: Color): Double {
        val r = toLinear(color.red.toDouble())
        val g = toLinear(color.green.toDouble())
        val b = toLinear(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = max(la, lb)
        val lo = min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun toOklch(color: Color): DoubleArray {
        val r = toLinear(color.red.toDouble())
        val g = toLinear(color.green.toDouble())
        val b = toLinear(color.blue.toDouble())
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
        val A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
        val B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
        return doubleArrayOf(L, hypot(A, B), atan2(B, A))
    }

    private fun fromOklch(L: Double, C: Double, H: Double): DoubleArray {
        val A = C * cos(H)
        val B = C * sin(H)
        val l = (L + 0.3963377774 * A + 0.2158037573 * B).pow(3.0)
        val m = (L - 0.1055613458 * A - 0.0638541728 * B).pow(3.0)
        val s = (L - 0.0894841775 * A - 1.291485548 * B).pow(3.0)
        return doubleArrayOf(
            toGamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            toGamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            toGamma(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
        )
    }

    private fun inGamut(rgb: DoubleArray): Boolean =
        rgb.all { it >= -1e-4 && it <= 1.0 + 1e-4 }

    private fun rgbToColor(rgb: DoubleArray): Color {
        val r = (rgb[0].coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val g = (rgb[1].coerceIn(0.0, 1.0) * 255.0).roundToInt()
        val b = (rgb[2].coerceIn(0.0, 1.0) * 255.0).roundToInt()
        return Color(r, g, b)
    }

    private fun mapped(L: Double, C: Double, H: Double): Color {
        val direct = fromOklch(L, C, H)
        if (inGamut(direct)) return rgbToColor(direct)
        var lo = 0.0
        var hi = C
        repeat(20) {
            val mid = (lo + hi) / 2.0
            if (inGamut(fromOklch(L, mid, H))) lo = mid else hi = mid
        }
        return rgbToColor(fromOklch(L, lo, H))
    }

    private fun bestOn(accent: Color, isDark: Boolean): Pair<Color, Double> {
        val candidates = if (isDark) {
            listOf(DarkKoalaColors.onAccent, Color.White)
        } else {
            listOf(Color.White, LightKoalaColors.ink)
        }
        return candidates
            .map { on -> on to contrast(on, accent) }
            .maxBy { it.second }
    }

    private fun readable(accent: Color, isDark: Boolean): Boolean {
        val surfaces = if (isDark) {
            listOf(
                DarkKoalaColors.bg,
                DarkKoalaColors.card,
                DarkKoalaColors.panel,
                DarkKoalaColors.sel,
                DarkKoalaColors.curBg,
            )
        } else {
            listOf(
                LightKoalaColors.bg,
                LightKoalaColors.card,
                LightKoalaColors.panel,
            )
        }
        return surfaces.all { contrast(accent, it) >= MIN_CONTRAST } &&
            bestOn(accent, isDark).second >= MIN_CONTRAST
    }

    private fun variant(base: Color, isDark: Boolean): CustomAccentPair {
        if (readable(base, isDark)) {
            return CustomAccentPair(
                accent = base,
                onAccent = bestOn(base, isDark).first,
                adjusted = false,
            )
        }
        val (l0, c, h) = toOklch(base)
        val dir = if (isDark) 1.0 else -1.0
        var l = l0
        while (l in 0.0..1.0) {
            val candidate = mapped(l, c, h)
            if (readable(candidate, isDark)) {
                return CustomAccentPair(
                    accent = candidate,
                    onAccent = bestOn(candidate, isDark).first,
                    adjusted = true,
                )
            }
            l += dir * 0.005
        }
        val fallback = if (isDark) DarkKoalaColors else LightKoalaColors
        return CustomAccentPair(
            accent = fallback.accent,
            onAccent = fallback.onAccent,
            adjusted = true,
        )
    }

    fun deriveCustomAccent(hex: String): DerivedCustomAccent {
        val base = parseHexColor(hex) ?: LightKoalaColors.accent
        val light = variant(base, isDark = false)
        val dark = variant(base, isDark = true)
        return DerivedCustomAccent(
            light = light,
            dark = dark,
            adjusted = light.adjusted || dark.adjusted,
        )
    }
}

fun KoalaColors.withAccent(
    preset: KoalaAccentPreset,
    isDark: Boolean,
    customHex: String = "#7B4BD6",
): KoalaColors {
    val (accentColor, onAccentColor) = if (preset == KoalaAccentPreset.CUSTOM) {
        val derived = KoalaAccentCalculator.deriveCustomAccent(customHex)
        val pair = if (isDark) derived.dark else derived.light
        pair.accent to pair.onAccent
    } else {
        if (isDark) {
            preset.darkAccent to preset.darkOnAccent
        } else {
            preset.lightAccent to preset.lightOnAccent
        }
    }
    return if (isDark) {
        copy(
            accent = accentColor,
            onAccent = onAccentColor,
            selInk = accentColor,
            curInk = accentColor,
        )
    } else {
        copy(
            accent = accentColor,
            onAccent = onAccentColor,
        )
    }
}

@Immutable
data class KoalaTypography(
    val displayFamily: FontFamily,
    val bodyFamily: FontFamily,
    val scale: Float,
) {
    val size12: TextStyle
        get() = TextStyle(fontFamily = bodyFamily, fontSize = (12f * scale).sp, lineHeight = (16f * scale).sp)
    val size13: TextStyle
        get() = TextStyle(fontFamily = bodyFamily, fontSize = (13f * scale).sp, lineHeight = (18f * scale).sp)
    val size14: TextStyle
        get() = TextStyle(fontFamily = bodyFamily, fontSize = (14f * scale).sp, lineHeight = (20f * scale).sp)
    val size15: TextStyle
        get() = TextStyle(fontFamily = bodyFamily, fontSize = (15f * scale).sp, lineHeight = (22f * scale).sp)
    val size18: TextStyle
        get() = TextStyle(fontFamily = displayFamily, fontSize = (18f * scale).sp, lineHeight = (24f * scale).sp)
    val size24: TextStyle
        get() = TextStyle(fontFamily = displayFamily, fontSize = (24f * scale).sp, lineHeight = (30f * scale).sp)
    val size34: TextStyle
        get() = TextStyle(fontFamily = displayFamily, fontSize = (34f * scale).sp, lineHeight = (40f * scale).sp)
    val size56: TextStyle
        get() = TextStyle(fontFamily = displayFamily, fontSize = (56f * scale).sp, lineHeight = (62f * scale).sp)
    val time: TextStyle
        get() = TextStyle(fontFamily = displayFamily, fontSize = (38f * scale).sp, lineHeight = (42f * scale).sp)
    val label: TextStyle
        get() = TextStyle(
            fontFamily = bodyFamily,
            fontSize = (12f * scale).sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
        )
}

val LocalKoalaColors = staticCompositionLocalOf { DarkKoalaColors }
val LocalKoalaTypography = staticCompositionLocalOf {
    KoalaTypography(
        displayFamily = FontFamily.Serif,
        bodyFamily = FontFamily.SansSerif,
        scale = 1.0f,
    )
}
val LocalKoalaSpinEnabled = staticCompositionLocalOf { true }
val LocalKoalaReducedMotion = staticCompositionLocalOf { false }

object KoalaTheme {
    val colors: KoalaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKoalaColors.current

    val typography: KoalaTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalKoalaTypography.current

    val spinEnabled: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalKoalaSpinEnabled.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalKoalaReducedMotion.current
}

private fun KoalaColors.toMaterialColorScheme(isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = sel,
            onPrimaryContainer = selInk,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = ctrl,
            onSecondaryContainer = ctrlInk,
            background = bg,
            onBackground = ink,
            surface = card,
            onSurface = ink,
            surfaceVariant = panel,
            onSurfaceVariant = muted,
            outline = line,
            outlineVariant = track,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = sel,
            onPrimaryContainer = selInk,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = ctrl,
            onSecondaryContainer = ctrlInk,
            background = bg,
            onBackground = ink,
            surface = card,
            onSurface = ink,
            surfaceVariant = panel,
            onSurfaceVariant = muted,
            outline = line,
            outlineVariant = track,
        )
    }
}

@Composable
fun KoalaShelfTheme(
    themeMode: KoalaThemeMode = KoalaThemeMode.SYSTEM,
    accentPreset: KoalaAccentPreset = KoalaAccentPreset.VINYL,
    customAccentHex: String = "#7B4BD6",
    fontChoice: KoalaFontChoice = KoalaFontChoice.STANDARD,
    textSize: KoalaTextSize = KoalaTextSize.MD,
    motionMode: KoalaMotionMode = KoalaMotionMode.SYSTEM,
    spinEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        KoalaThemeMode.LIGHT -> false
        KoalaThemeMode.DARK -> true
        KoalaThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColors = if (isDark) DarkKoalaColors else LightKoalaColors
    val koalaColors = baseColors.withAccent(accentPreset, isDark, customAccentHex)

    val (displayFamily, bodyFamily) = fontChoice.displayFontFamily() to fontChoice.bodyFontFamily()

    val koalaTypography = KoalaTypography(
        displayFamily = displayFamily,
        bodyFamily = bodyFamily,
        scale = textSize.scale,
    )

    val materialTypography = Typography(
        displayLarge = koalaTypography.size56,
        displayMedium = koalaTypography.size34,
        headlineMedium = koalaTypography.size24,
        titleLarge = koalaTypography.size18,
        titleMedium = koalaTypography.size15.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = koalaTypography.size15,
        bodyMedium = koalaTypography.size14,
        bodySmall = koalaTypography.size13,
        labelSmall = koalaTypography.label,
    )

    val reducedMotion = motionMode == KoalaMotionMode.REDUCE

    CompositionLocalProvider(
        LocalKoalaColors provides koalaColors,
        LocalKoalaTypography provides koalaTypography,
        LocalKoalaSpinEnabled provides (spinEnabled && !reducedMotion),
        LocalKoalaReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = koalaColors.toMaterialColorScheme(isDark),
            typography = materialTypography,
            content = content,
        )
    }
}
