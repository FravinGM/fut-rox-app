package br.futrox.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.futrox.core.Team

/** Paleta extraída do CSS do site (fut-rox.vercel.app). */
object Brand {
    val Green = Color(0xFF173E31)
    val GreenDark = Color(0xFF10372D)
    val Lime = Color(0xFFD8F494)
    val Gold = Color(0xFFB79643)
    val BgLight = Color(0xFFF7F8F2)
    val BgDark = Color(0xFF101916)
    val SurfaceDark = Color(0xFF16231E)
    val LineLight = Color(0xFFE3E7DD)
    val LineDark = Color(0xFF30433A)
    val MutedLight = Color(0xFF7A837B)
    val MutedDark = Color(0xFFADBBAD)
    val InkLight = Color(0xFF24392E)
    val InkDark = Color(0xFFE6EEE5)
}

val Barlow = FontFamily(
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val DmSans = FontFamily(
    Font(R.font.dmsans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
    Font(R.font.dmsans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(550))),
    Font(R.font.dmsans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val LocalDarkTheme = compositionLocalOf { false }
/** Alterna claro/escuro (e lembra a escolha). */
val LocalToggleTheme = compositionLocalOf { {} }

/** Fundo e texto do botão de cada time (A = verde/lima do site, B = dourado). */
@Composable
fun teamColors(t: Team): Pair<Color, Color> {
    val dark = LocalDarkTheme.current
    return when {
        t == Team.B -> Brand.Gold to Brand.GreenDark
        dark -> Brand.Lime to Brand.GreenDark
        else -> Brand.Green to Brand.Lime
    }
}

@Composable
fun FutTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Brand.Lime, onPrimary = Brand.GreenDark,
        secondary = Brand.Gold, onSecondary = Brand.GreenDark,
        background = Brand.BgDark, onBackground = Brand.InkDark,
        surface = Brand.SurfaceDark, onSurface = Brand.InkDark,
        surfaceVariant = Brand.SurfaceDark, onSurfaceVariant = Brand.MutedDark,
        outline = Brand.LineDark, outlineVariant = Brand.LineDark,
    ) else lightColorScheme(
        primary = Brand.Green, onPrimary = Brand.Lime,
        secondary = Brand.Gold, onSecondary = Brand.GreenDark,
        background = Brand.BgLight, onBackground = Brand.InkLight,
        surface = Color.White, onSurface = Brand.InkLight,
        surfaceVariant = Color.White, onSurfaceVariant = Brand.MutedLight,
        outline = Brand.LineLight, outlineVariant = Brand.LineLight,
    )
    val base = Typography()
    fun TextStyle.dm() = copy(fontFamily = DmSans)
    fun TextStyle.title() = copy(fontFamily = Barlow, fontWeight = FontWeight.Bold)
    val typography = Typography(
        displayLarge = base.displayLarge.title(), displayMedium = base.displayMedium.title(), displaySmall = base.displaySmall.title(),
        headlineLarge = base.headlineLarge.title(), headlineMedium = base.headlineMedium.title(), headlineSmall = base.headlineSmall.title(),
        titleLarge = base.titleLarge.title(), titleMedium = base.titleMedium.dm(), titleSmall = base.titleSmall.dm(),
        bodyLarge = base.bodyLarge.dm(), bodyMedium = base.bodyMedium.dm(), bodySmall = base.bodySmall.dm(),
        labelLarge = base.labelLarge.dm().copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelMedium = base.labelMedium.dm(), labelSmall = base.labelSmall.dm(),
    )
    CompositionLocalProvider(LocalDarkTheme provides dark) { MaterialTheme(
        colorScheme = scheme, typography = typography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp),
        ),
        content = content,
    )
} }
