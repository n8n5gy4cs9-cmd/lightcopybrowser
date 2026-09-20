package dev.lightcopy.browser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.lightcopy.browser.settings.AppearanceMode

data class LightCopyPalette(
    val ink: Color,
    val surface: Color,
    val raised: Color,
    val cyan: Color,
    val mint: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
)

val DarkPalette = LightCopyPalette(
    ink = Color(0xFF090D12),
    surface = Color(0xFF101720),
    raised = Color(0xFF17212C),
    cyan = Color(0xFF5DD6FF),
    mint = Color(0xFF8DF5C4),
    textPrimary = Color(0xFFEDF7FF),
    textSecondary = Color(0xFF9FB0C0),
    divider = Color(0xFF263442),
)

val LightPalette = LightCopyPalette(
    ink = Color(0xFFF5F8FA),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE5EDF2),
    cyan = Color(0xFF006B8D),
    mint = Color(0xFF0E7C59),
    textPrimary = Color(0xFF13191D),
    textSecondary = Color(0xFF3F4B53),
    divider = Color(0xFFC7D3DB),
)

val AmoledPalette = LightCopyPalette(
    ink = Color.Black,
    surface = Color.Black,
    raised = Color(0xFF0B1117),
    cyan = Color(0xFF5DD6FF),
    mint = Color(0xFF8DF5C4),
    textPrimary = Color(0xFFEDF7FF),
    textSecondary = Color(0xFF9FB0C0),
    divider = Color(0xFF263442),
)

val LocalLightCopyPalette = staticCompositionLocalOf { DarkPalette }

val Ink: Color @Composable get() = LocalLightCopyPalette.current.ink
val Surface: Color @Composable get() = LocalLightCopyPalette.current.surface
val Raised: Color @Composable get() = LocalLightCopyPalette.current.raised
val Cyan: Color @Composable get() = LocalLightCopyPalette.current.cyan
val Mint: Color @Composable get() = LocalLightCopyPalette.current.mint
val TextPrimary: Color @Composable get() = LocalLightCopyPalette.current.textPrimary
val TextSecondary: Color @Composable get() = LocalLightCopyPalette.current.textSecondary
val Divider: Color @Composable get() = LocalLightCopyPalette.current.divider

@Composable
fun LightCopyTheme(appearance: AppearanceMode = AppearanceMode.Dark, primaryHex: String? = null, secondaryHex: String? = null, content: @Composable () -> Unit) {
    val palette = when (appearance) {
        AppearanceMode.Light -> LightPalette
        AppearanceMode.Dark -> DarkPalette
        AppearanceMode.Amoled -> AmoledPalette
    }
    val customPalette = palette.copy(
        cyan = primaryHex?.let { Color(android.graphics.Color.parseColor("#$it")) } ?: palette.cyan,
        mint = secondaryHex?.let { Color(android.graphics.Color.parseColor("#$it")) } ?: palette.mint,
    )
    val scheme = if (appearance == AppearanceMode.Light) {
        lightColorScheme(
            primary = customPalette.cyan,
            secondary = customPalette.mint,
            background = palette.ink,
            surface = palette.surface,
            surfaceVariant = palette.raised,
            onPrimary = palette.ink,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
        )
    } else {
        darkColorScheme(
            primary = customPalette.cyan,
            secondary = customPalette.mint,
            background = palette.ink,
            surface = palette.surface,
            surfaceVariant = palette.raised,
            onPrimary = DarkPalette.ink,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
        )
    }
    CompositionLocalProvider(LocalLightCopyPalette provides customPalette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
