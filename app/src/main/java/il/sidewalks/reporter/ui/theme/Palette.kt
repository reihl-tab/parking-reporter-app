package il.sidewalks.reporter.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * App colour palette as plain RGB values (no Compose types), so plain JVM unit
 * tests can check contrast without an Android runtime.
 *
 * Source: the owner's 2026-11-09 screenshot of the current Jerusalem
 * municipality site (mobile main page), issue #161 item 1. Navy header
 * `#2C537E`, gold action `#D8A038`, row grey `#F7F7F7`, divider `#B3C1CE`.
 * Visual reference only: no emblem or logo asset is used (owner instruction).
 * There is no purple role and no "Jerusalem stone" beige.
 *
 * Rule from the contrast audit: gold is a container colour only, never text or
 * an icon on a light background (2.33:1 on white), and navy text never sits on
 * gold (3.40:1). Text on gold uses dark brown `onSecondary`.
 */
object Palette {
    /** One full Material 3 role set, so no role falls back to the M3 baseline purple. */
    data class Roles(
        val primary: Int, val onPrimary: Int,
        val primaryContainer: Int, val onPrimaryContainer: Int,
        val inversePrimary: Int,
        val secondary: Int, val onSecondary: Int,
        val secondaryContainer: Int, val onSecondaryContainer: Int,
        val tertiary: Int, val onTertiary: Int,
        val tertiaryContainer: Int, val onTertiaryContainer: Int,
        val background: Int, val onBackground: Int,
        val surface: Int, val onSurface: Int,
        val surfaceVariant: Int, val onSurfaceVariant: Int,
        val surfaceTint: Int,
        val inverseSurface: Int, val inverseOnSurface: Int,
        val error: Int, val onError: Int,
        val errorContainer: Int, val onErrorContainer: Int,
        val outline: Int, val outlineVariant: Int,
        val scrim: Int,
        val surfaceBright: Int, val surfaceDim: Int,
        val surfaceContainer: Int, val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
        val surfaceContainerLow: Int, val surfaceContainerLowest: Int,
    ) {
        fun all(): List<Int> = listOf(
            primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
            secondary, onSecondary, secondaryContainer, onSecondaryContainer,
            tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
            background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
            surfaceTint, inverseSurface, inverseOnSurface,
            error, onError, errorContainer, onErrorContainer, outline, outlineVariant, scrim,
            surfaceBright, surfaceDim, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
            surfaceContainerLow, surfaceContainerLowest,
        )
    }

    /** Municipal navy: header background and status bar in both themes. */
    const val NAVY = 0x2C537E
    const val GOLD = 0xD8A038

    val light = Roles(
        primary = NAVY, onPrimary = 0xFFFFFF,
        primaryContainer = 0xD5E3F5, onPrimaryContainer = 0x0B1D33,
        inversePrimary = 0xA3C9F5,
        secondary = GOLD, onSecondary = 0x261900,
        secondaryContainer = 0xF7E3B8, onSecondaryContainer = 0x3A2A05,
        // The site's second blue (older public CSS family) instead of M3's pink-purple tertiary.
        tertiary = 0x0F6EA9, onTertiary = 0xFFFFFF,
        tertiaryContainer = 0xCFE5FF, onTertiaryContainer = 0x001D33,
        background = 0xFFFFFF, onBackground = 0x1A1C1E,
        surface = 0xF7F7F7, onSurface = 0x1A1C1E,
        surfaceVariant = 0xE4ECF4, onSurfaceVariant = 0x42474E,
        surfaceTint = NAVY,
        inverseSurface = 0x2F3033, inverseOnSurface = 0xF1F0F4,
        // M3 default error family, kept as specified.
        error = 0xBA1A1A, onError = 0xFFFFFF,
        errorContainer = 0xFFDAD6, onErrorContainer = 0x410002,
        outline = 0xB3C1CE, outlineVariant = 0xD7DEE6,
        scrim = 0x000000,
        surfaceBright = 0xFFFFFF, surfaceDim = 0xD9DDE2,
        surfaceContainer = 0xF1F3F6, surfaceContainerHigh = 0xEBEEF2, surfaceContainerHighest = 0xE4ECF4,
        surfaceContainerLow = 0xF7F7F7, surfaceContainerLowest = 0xFFFFFF,
    )

    /** Derived dark scheme: light-blue primary and warm gold accent on neutral dark surfaces. */
    val dark = Roles(
        primary = 0xA3C9F5, onPrimary = 0x08305A,
        primaryContainer = NAVY, onPrimaryContainer = 0xD5E3F5,
        inversePrimary = NAVY,
        secondary = 0xF0C26E, onSecondary = 0x261900,
        secondaryContainer = 0x5C4300, onSecondaryContainer = 0xF7E3B8,
        tertiary = 0x93CCFF, onTertiary = 0x003352,
        tertiaryContainer = 0x0F4C75, onTertiaryContainer = 0xCFE5FF,
        background = 0x111418, onBackground = 0xE2E2E6,
        surface = 0x1A1C1E, onSurface = 0xE2E2E6,
        surfaceVariant = 0x42474E, onSurfaceVariant = 0xC2C7CF,
        surfaceTint = 0xA3C9F5,
        inverseSurface = 0xE2E2E6, inverseOnSurface = 0x2F3033,
        error = 0xFFB4AB, onError = 0x690005,
        errorContainer = 0x93000A, onErrorContainer = 0xFFDAD6,
        outline = 0x8C9199, outlineVariant = 0x42474E,
        scrim = 0x000000,
        surfaceBright = 0x37393E, surfaceDim = 0x111418,
        surfaceContainer = 0x1E2024, surfaceContainerHigh = 0x282A2E, surfaceContainerHighest = 0x333539,
        surfaceContainerLow = 0x1A1C1E, surfaceContainerLowest = 0x0C0E11,
    )

    /** WCAG 2.x relative luminance of an sRGB `0xRRGGBB` value. */
    fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG 2.x contrast ratio, 1.0..21.0. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
