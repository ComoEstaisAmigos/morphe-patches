package app.deadcrossing.patches.shared

import app.adebisi.patches.shared.requireSigner
import app.adebisi.patches.shared.signerCertSha256

/** Signer #1 certificate SHA-256 digest of Feelingtouch's v1 key (CN=Feelingtouch, Hangzhou). */
internal const val FEELINGTOUCH_CERT_SHA256 =
    "02a90ad1600a0607ca1580dc0205ab246d80f2bf5819b9361b842e92bcfc3e88"

internal fun requireFeelingtouchSigner(original: String) {
    requireSigner(signerCertSha256(), FEELINGTOUCH_CERT_SHA256, "Feelingtouch", original)
}
