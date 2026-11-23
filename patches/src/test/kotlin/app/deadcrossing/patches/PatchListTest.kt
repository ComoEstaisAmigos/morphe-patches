package app.deadcrossing.patches

import app.deadcrossing.patches.compat.emptyYoumiSdksPatch
import app.deadcrossing.patches.mono.unlimitedCurrencyPatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PATCHES = listOf(
    unlimitedCurrencyPatch,
)

class PatchListTest {
    @Test
    fun `every Dead Crossing patch is on by default`() {
        assertEquals(
            mapOf(
                "Unlimited currency" to true,
            ),
            PATCHES.associate { it.name!! to it.default },
        )
    }

    @Test
    fun `youmi empty is unnamed and every visible patch depends on it`() {
        assertNull(emptyYoumiSdksPatch.name)
        for (patch in PATCHES) {
            assertTrue(emptyYoumiSdksPatch in patch.dependencies, patch.name)
        }
    }

    @Test
    fun `no description uses a dash as punctuation`() {
        for (patch in PATCHES) {
            val description = patch.description ?: continue
            assertFalse(Regex("[\u2013\u2014]").containsMatchIn(description), patch.name)
        }
    }

    @Test
    fun `no description names the game`() {
        for (patch in PATCHES) {
            val description = patch.description ?: continue
            assertFalse("Dead Crossing" in description, patch.name)
            assertFalse("Feelingtouch" in description, patch.name)
        }
    }

    @Test
    fun `currency description matches the other games`() {
        assertEquals(
            "A fresh install starts with 99,999,999 Glory and 99,999,999 Gold. " +
                "If you install the patched app as an update, your current balance stays as it is.",
            unlimitedCurrencyPatch.description,
        )
    }
}
