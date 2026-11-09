package il.sidewalks.reporter.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards issue #161's palette: WCAG AA for text roles, and no M3 baseline purple. */
class ThemeContrastTest {
    private val aa = 4.5

    private fun assertAa(name: String, background: Int, foreground: Int) {
        val ratio = Palette.contrast(background, foreground)
        assertTrue("$name contrast %.2f:1 is below AA %.1f:1".format(ratio, aa), ratio >= aa)
    }

    private fun textPairs(r: Palette.Roles) = listOf(
        Triple("primary/onPrimary", r.primary, r.onPrimary),
        Triple("primaryContainer/onPrimaryContainer", r.primaryContainer, r.onPrimaryContainer),
        Triple("secondary/onSecondary", r.secondary, r.onSecondary),
        Triple("secondaryContainer/onSecondaryContainer", r.secondaryContainer, r.onSecondaryContainer),
        Triple("tertiary/onTertiary", r.tertiary, r.onTertiary),
        Triple("tertiaryContainer/onTertiaryContainer", r.tertiaryContainer, r.onTertiaryContainer),
        Triple("background/onBackground", r.background, r.onBackground),
        Triple("surface/onSurface", r.surface, r.onSurface),
        Triple("surfaceVariant/onSurfaceVariant", r.surfaceVariant, r.onSurfaceVariant),
        Triple("error/onError", r.error, r.onError),
        Triple("errorContainer/onErrorContainer", r.errorContainer, r.onErrorContainer),
        Triple("inverseSurface/inverseOnSurface", r.inverseSurface, r.inverseOnSurface),
        // Primary is also used as text/icon colour (links, selected items, text buttons).
        Triple("surface/primary", r.surface, r.primary),
        Triple("background/primary", r.background, r.primary),
    )

    @Test fun lightSchemeTextPairsMeetAa() {
        textPairs(Palette.light).forEach { (name, bg, fg) -> assertAa("light $name", bg, fg) }
    }

    @Test fun darkSchemeTextPairsMeetAa() {
        textPairs(Palette.dark).forEach { (name, bg, fg) -> assertAa("dark $name", bg, fg) }
    }

    @Test fun contrastFormulaMatchesIssueAudit() {
        // Values computed in the #161 spec; a formula regression would shift them.
        assertEquals(7.94, Palette.contrast(Palette.NAVY, 0xFFFFFF), 0.01)
        assertEquals(7.37, Palette.contrast(Palette.GOLD, 0x261900), 0.01)
        assertEquals(15.95, Palette.contrast(0xF7F7F7, 0x1A1C1E), 0.01)
        assertEquals(21.0, Palette.contrast(0x000000, 0xFFFFFF), 0.001)
    }

    @Test fun goldIsNeverTextOnLightAndNavyNeverTextOnGold() {
        // The audit's two failing pairs: the scheme must not create them as on-colours.
        assertTrue(Palette.contrast(Palette.GOLD, 0xFFFFFF) < aa)
        assertTrue(Palette.contrast(Palette.GOLD, Palette.NAVY) < aa)
        val light = Palette.light
        assertFalse(light.onBackground == Palette.GOLD || light.onSurface == Palette.GOLD || light.onPrimary == Palette.GOLD)
        assertFalse(light.onSecondary == Palette.NAVY)
    }

    @Test fun noMaterialBaselinePurpleInAnyRole() {
        // M3 baseline (purple) primary/secondary/tertiary families, light and dark, plus the baseline surfaces.
        val baseline = setOf(
            0x6750A4, 0xEADDFF, 0x21005D, 0x625B71, 0xE8DEF8, 0x1D192B, 0x7D5260, 0xFFD8E4, 0x31111D,
            0xD0BCFF, 0x381E72, 0x4F378B, 0xCCC2DC, 0x332D41, 0x4A4458, 0xEFB8C8, 0x492532, 0x633B48,
            0xFFFBFE, 0x1C1B1F, 0xE7E0EC, 0x49454F, 0x79747E, 0xF3EDF7, 0xCAC4D0,
        )
        listOf("light" to Palette.light, "dark" to Palette.dark).forEach { (name, roles) ->
            roles.all().forEach { rgb ->
                assertFalse("$name scheme uses M3 baseline colour %06X".format(rgb), rgb in baseline)
            }
        }
    }

    @Test fun statusBarNavyIsTheMunicipalHeaderColour() {
        assertEquals(0x2C537E, Palette.NAVY)
        assertEquals(Palette.NAVY, Palette.light.primary)
        assertEquals(36, Palette.light.all().size)
    }
}
