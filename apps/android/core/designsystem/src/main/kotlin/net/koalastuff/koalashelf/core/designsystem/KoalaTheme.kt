package net.koalastuff.koalashelf.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalKoalaColors = staticCompositionLocalOf { LightKoalaColors }

/** Access to the KoalaShelf color tokens of the current theme. */
object Koala {
    val colors: KoalaColors
        @Composable @ReadOnlyComposable get() = LocalKoalaColors.current
}

val Gloock = FontFamily(Font(R.font.gloock_regular))

private fun manrope(weight: Int) = Font(
    R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(600), manrope(800))

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Gloock, fontSize = KoalaTypeScale.size56, lineHeight = 56.sp),
    headlineLarge = TextStyle(fontFamily = Gloock, fontSize = KoalaTypeScale.size34, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Gloock, fontSize = KoalaTypeScale.size24, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = Gloock, fontSize = KoalaTypeScale.size18, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(800), fontSize = KoalaTypeScale.size15),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(600), fontSize = KoalaTypeScale.size14),
    bodyLarge = TextStyle(fontFamily = Manrope, fontSize = KoalaTypeScale.size15, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontSize = KoalaTypeScale.size14, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontSize = KoalaTypeScale.size13, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(800), fontSize = KoalaTypeScale.size14),
    labelSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight(800),
        fontSize = KoalaTypeScale.label,
        letterSpacing = 1.2.sp,
    ),
)

/** Player time display (Gloock 38). */
val TimeStyle = TextStyle(fontFamily = Gloock, fontSize = KoalaTypeScale.time, lineHeight = 40.sp)

@Composable
fun KoalaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkKoalaColors else LightKoalaColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.ink,
            surface = c.bg, onSurface = c.ink, surfaceVariant = c.card, onSurfaceVariant = c.muted,
            surfaceContainer = c.card, outline = c.line, secondaryContainer = c.sel, onSecondaryContainer = c.selInk,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.ink,
            surface = c.bg, onSurface = c.ink, surfaceVariant = c.card, onSurfaceVariant = c.muted,
            surfaceContainer = c.card, outline = c.line, secondaryContainer = c.sel, onSecondaryContainer = c.selInk,
        )
    }
    CompositionLocalProvider(LocalKoalaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
