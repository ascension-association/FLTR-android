package org.ascensionassociation.fltr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ascensionassociation.fltr.R

// ---- Brand palette, sampled from the official astronaut artwork ----------
// Bold plum outlines, white and lavender fills, sky-blue and red accents.

object Brand {
    val Plum = Color(0xFF371A45)       // the artwork's outline colour
    val PlumDeep = Color(0xFF220F2D)   // helmet visor
    val Lavender = Color(0xFFB9AFBE)   // shading
    val LavenderLight = Color(0xFFEDE7F2)
    val Sky = Color(0xFF33AEF1)        // laptop screen
    val SkyDeep = Color(0xFF0B76B3)    // accessible on white
    val SkySoft = Color(0xFFD6EFFC)
    val Red = Color(0xFFF92323)        // laptop base
    val RedDeep = Color(0xFFC81E28)
    val RedSoft = Color(0xFFFFE2E0)
    val Canvas = Color(0xFFFBF8FD)
    val Night = Color(0xFF170B1F)
}

private val LightColors = lightColorScheme(
    primary = Brand.Plum,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADCF3),
    onPrimaryContainer = Color(0xFF2A1236),
    secondary = Brand.SkyDeep,
    onSecondary = Color.White,
    secondaryContainer = Brand.SkySoft,
    onSecondaryContainer = Color(0xFF053A57),
    tertiary = Brand.RedDeep,
    onTertiary = Color.White,
    tertiaryContainer = Brand.RedSoft,
    onTertiaryContainer = Color(0xFF5C0A10),
    background = Brand.Canvas,
    onBackground = Color(0xFF1F1426),
    surface = Brand.Canvas,
    onSurface = Color(0xFF1F1426),
    surfaceVariant = Color(0xFFEFE9F3),
    onSurfaceVariant = Color(0xFF5B4D63),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF2ECF6),
    surfaceContainerHigh = Color(0xFFECE5F1),
    surfaceContainerHighest = Color(0xFFE6DEEC),
    outline = Color(0xFF7D6E86),
    outlineVariant = Color(0xFFD9CFE0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE3C9F2),
    onPrimary = Color(0xFF3A1D49),
    primaryContainer = Color(0xFF52306A),
    onPrimaryContainer = Color(0xFFF4E6FB),
    secondary = Color(0xFF86D1FA),
    onSecondary = Color(0xFF003450),
    secondaryContainer = Color(0xFF0B4F75),
    onSecondaryContainer = Brand.SkySoft,
    tertiary = Color(0xFFFFB3AE),
    onTertiary = Color(0xFF68000F),
    tertiaryContainer = Color(0xFF8E1520),
    onTertiaryContainer = Brand.RedSoft,
    background = Brand.Night,
    onBackground = Color(0xFFEDE3F2),
    surface = Brand.Night,
    onSurface = Color(0xFFEDE3F2),
    surfaceVariant = Color(0xFF3A2C44),
    onSurfaceVariant = Color(0xFFCFC1D8),
    surfaceContainerLowest = Color(0xFF12081A),
    surfaceContainerLow = Color(0xFF1F1228),
    surfaceContainer = Color(0xFF241630),
    surfaceContainerHigh = Color(0xFF2E2039),
    surfaceContainerHighest = Color(0xFF392B44),
    outline = Color(0xFF9C8CA6),
    outlineVariant = Color(0xFF4A3B54),
)

/** Colours outside Material's roles: the "sticker" outline and status accents. */
@Immutable
data class FltrColors(
    val stroke: Color,
    val good: Color,
    val goodContainer: Color,
    val warn: Color,
    val warnContainer: Color,
)

private val LightExtra = FltrColors(
    stroke = Brand.Plum,
    good = Brand.SkyDeep,
    goodContainer = Brand.SkySoft,
    warn = Color(0xFF8A5A00),
    warnContainer = Color(0xFFFFEBC2),
)

private val DarkExtra = FltrColors(
    stroke = Color(0xFF5D4769),
    good = Color(0xFF86D1FA),
    goodContainer = Color(0xFF0B4F75),
    warn = Color(0xFFFFCF70),
    warnContainer = Color(0xFF5A3E00),
)

val LocalFltrColors = staticCompositionLocalOf { LightExtra }

// ---- Type: Nunito (variable font, bundled) --------------------------------

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(
    nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900),
)

private fun TextStyle.n(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

private val Base = Typography()

private val FltrTypography = Typography(
    displayLarge = Base.displayLarge.n(FontWeight.Black),
    displayMedium = Base.displayMedium.n(FontWeight.Black),
    displaySmall = Base.displaySmall.n(FontWeight.ExtraBold),
    headlineLarge = Base.headlineLarge.n(FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.n(FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.n(FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.n(FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.n(FontWeight.Bold).copy(fontSize = 17.sp),
    titleSmall = Base.titleSmall.n(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.n(FontWeight.Medium),
    bodyMedium = Base.bodyMedium.n(FontWeight.Medium),
    bodySmall = Base.bodySmall.n(FontWeight.Medium),
    labelLarge = Base.labelLarge.n(FontWeight.Bold),
    labelMedium = Base.labelMedium.n(FontWeight.Bold),
    labelSmall = Base.labelSmall.n(FontWeight.Bold),
)

// Generous, friendly corners to match the illustration's rounded shapes.
private val FltrShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FltrTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkColors else LightColors
    androidx.compose.runtime.CompositionLocalProvider(
        LocalFltrColors provides if (darkTheme) DarkExtra else LightExtra,
    ) {
        MaterialTheme(colorScheme = scheme, typography = FltrTypography, shapes = FltrShapes, content = content)
    }
}

object FltrTheme {
    val colors: FltrColors
        @Composable get() = LocalFltrColors.current
}
