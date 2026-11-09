package il.sidewalks.reporter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Opaque Compose colour from a `0xRRGGBB` palette value. */
internal fun opaque(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)

val Navy = opaque(Palette.NAVY)

/** Every role is set explicitly so none falls back to the M3 baseline (purple). */
private fun Palette.Roles.scheme(dark: Boolean): ColorScheme {
    val c = ::opaque
    return if (dark) darkColorScheme(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        inversePrimary = c(inversePrimary),
        secondary = c(secondary), onSecondary = c(onSecondary),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(background), onBackground = c(onBackground),
        surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
        surfaceTint = c(surfaceTint),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        error = c(error), onError = c(onError),
        errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        outline = c(outline), outlineVariant = c(outlineVariant), scrim = c(scrim),
        surfaceBright = c(surfaceBright), surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh), surfaceContainerHighest = c(surfaceContainerHighest),
        surfaceContainerLow = c(surfaceContainerLow), surfaceContainerLowest = c(surfaceContainerLowest),
        surfaceDim = c(surfaceDim),
    ) else lightColorScheme(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        inversePrimary = c(inversePrimary),
        secondary = c(secondary), onSecondary = c(onSecondary),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(background), onBackground = c(onBackground),
        surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
        surfaceTint = c(surfaceTint),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        error = c(error), onError = c(onError),
        errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        outline = c(outline), outlineVariant = c(outlineVariant), scrim = c(scrim),
        surfaceBright = c(surfaceBright), surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh), surfaceContainerHighest = c(surfaceContainerHighest),
        surfaceContainerLow = c(surfaceContainerLow), surfaceContainerLowest = c(surfaceContainerLowest),
        surfaceDim = c(surfaceDim),
    )
}

val AppLightColors: ColorScheme = Palette.light.scheme(dark = false)
val AppDarkColors: ColorScheme = Palette.dark.scheme(dark = true)
