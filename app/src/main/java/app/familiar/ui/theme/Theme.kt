package app.familiar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.familiar.R
import app.familiar.data.ThemeMode

/** A storybook: warm parchment by day, a candlelit study at night. Violet for magic, blue for mana, gold for treasure. */
@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val mana: Color,
    val manaSoft: Color,
    val gold: Color,
    val goldSoft: Color,
    val ember: Color,
    val emberSoft: Color,
    val leaf: Color,
    val leafSoft: Color,
    val danger: Color,
    /** The den behind the familiar: a glow and the dark around it. */
    val denGlow: Color,
    val denDark: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    bg = Color(0xFFF3EBDD),
    surface = Color(0xFFFFFAF1),
    surface2 = Color(0xFFF8F0E2),
    line = Color(0xFFE6D9C3),
    ink = Color(0xFF2B2233),
    muted = Color(0xFF6E6170),
    faint = Color(0xFFA59683),
    accent = Color(0xFF6A4BB5),
    accentSoft = Color(0xFFE8DFF6),
    onAccent = Color(0xFFFFFFFF),
    mana = Color(0xFF2E86C9),
    manaSoft = Color(0xFFDCEBF7),
    gold = Color(0xFFB4831F),
    goldSoft = Color(0xFFF4E6C4),
    ember = Color(0xFFD0662A),
    emberSoft = Color(0xFFF8E0CD),
    leaf = Color(0xFF3F8A5C),
    leafSoft = Color(0xFFDDEEE1),
    danger = Color(0xFFC0413A),
    denGlow = Color(0xFFFFE8B8),
    denDark = Color(0xFFE9DCC4),
    dark = false,
)

val DarkPalette = Palette(
    bg = Color(0xFF15121C),
    surface = Color(0xFF1E1A27),
    surface2 = Color(0xFF241F2F),
    line = Color(0xFF332C40),
    ink = Color(0xFFF1E9DC),
    muted = Color(0xFFB4A8B8),
    faint = Color(0xFF7D7186),
    accent = Color(0xFFAE93FF),
    accentSoft = Color(0xFF2F2650),
    onAccent = Color(0xFF170F2C),
    mana = Color(0xFF72C2FF),
    manaSoft = Color(0xFF15304A),
    gold = Color(0xFFE9C26A),
    goldSoft = Color(0xFF3A2F17),
    ember = Color(0xFFFF9A55),
    emberSoft = Color(0xFF3B2416),
    leaf = Color(0xFF7FCB98),
    leafSoft = Color(0xFF1B3325),
    danger = Color(0xFFFF6B5E),
    denGlow = Color(0xFF4A3A2A),
    denDark = Color(0xFF15121C),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** What a whole screen is painted with: the page colour, or nothing over a backdrop picture. */
val LocalScreen = staticCompositionLocalOf { Color.Unspecified }

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/** Old-book letters for titles and names. */
val Cormorant = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold),
)

object U {
    val c: Palette @Composable get() = LocalPalette.current
    /** A screen's own background: see [LocalScreen]. */
    val screen: Color @Composable get() = LocalScreen.current.takeOrElse { c.bg }

    val RXl = RoundedCornerShape(28.dp)
    val RLg = RoundedCornerShape(22.dp)
    val RMd = RoundedCornerShape(16.dp)
    val RSm = RoundedCornerShape(12.dp)
}

object UType {
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 31.sp, color = U.c.ink)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 14.5.sp, lineHeight = 21.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.14.em, color = U.c.faint)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 15.sp, lineHeight = 22.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = U.c.faint)
    val Number: TextStyle @Composable get() = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = U.c.ink, fontFeatureSettings = "lnum, tnum")
    val Timer: TextStyle @Composable get() = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Bold, fontSize = 58.sp, color = U.c.ink, fontFeatureSettings = "lnum, tnum")
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val Quote: TextStyle @Composable get() = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, color = U.c.ink)
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FamiliarTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme.copy(
                primary = p.accent,
                onPrimary = p.onAccent,
                primaryContainer = p.accentSoft,
                onPrimaryContainer = p.ink,
                secondaryContainer = p.accentSoft,
                onSecondaryContainer = p.ink,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface2,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
                error = p.danger,
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = UType.Body,
                bodyMedium = UType.Body.copy(fontSize = 14.sp),
                labelLarge = UType.Button,
            ),
            content = content,
        )
    }
}
