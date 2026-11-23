package app.deadcrossing.patches.shared

import app.morphe.patcher.patch.PatchException
import app.adebisi.patches.shared.requireSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignerTest {
    @Test
    fun `stock signer is accepted and a re-sign is refused`() {
        assertEquals(64, FEELINGTOUCH_CERT_SHA256.length)
        assertTrue(FEELINGTOUCH_CERT_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(FEELINGTOUCH_CERT_SHA256.startsWith("02a90ad1"))
        requireSigner(
            setOf(FEELINGTOUCH_CERT_SHA256),
            FEELINGTOUCH_CERT_SHA256,
            "Feelingtouch",
            "Dead Crossing 1.06 APK",
        )
        assertFailsWith<PatchException> {
            requireSigner(emptySet(), FEELINGTOUCH_CERT_SHA256, "Feelingtouch", "Dead Crossing 1.06 APK")
        }
        assertFailsWith<PatchException> {
            requireSigner(setOf("aa".repeat(32)), FEELINGTOUCH_CERT_SHA256, "Feelingtouch", "Dead Crossing 1.06 APK")
        }
        assertFailsWith<PatchException> {
            requireSigner(
                setOf(FEELINGTOUCH_CERT_SHA256, "bb".repeat(32)),
                FEELINGTOUCH_CERT_SHA256,
                "Feelingtouch",
                "Dead Crossing 1.06 APK",
            )
        }
    }
}
