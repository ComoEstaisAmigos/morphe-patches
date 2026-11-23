package app.deadcrossing.patches.mono

import app.adebisi.patches.shared.applyEdits
import app.adebisi.patches.shared.toHex
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What would have caught ads.feelingtouch.com DNS with Java stubs only:
 * FelAd still GETs the UTF-16 hosts in Assembly-CSharp-firstpass.dll.
 */
class DllEditsEval {
    @Test
    fun `stock firstpass still has the ads host CSharp would GET`() {
        val dll = zipBytes(originalApk(), DllEdits.FIRSTPASS_PATH)
        val ads = "ads.feelingtouch.com".toByteArray(Charsets.UTF_16LE)
        val gamebox = "gamebox.feelingtouch.com".toByteArray(Charsets.UTF_16LE)
        assertEquals(4, countHits(dll, ads))
        assertEquals(2, countHits(dll, gamebox))
        for (edit in DllEdits.FIRSTPASS_URLS) {
            val got = dll.copyOfRange(edit.offset, edit.offset + edit.old.size)
            assertTrue(got.contentEquals(edit.old), "0x${edit.offset.toString(16)}")
        }
        val patched = dll.applyEdits(DllEdits.HOSTS + DllEdits.FIRSTPASS_URLS)
        assertEquals(0, countHits(patched, ads))
        assertEquals(0, countHits(patched, gamebox))
        assertFalse("ads.feelingtouch.com".toByteArray().toHex() in patched.toHex())
        assertTrue("127.0.0.1".toByteArray(Charsets.UTF_16LE).toHex() in patched.toHex())
    }

    private fun originalApk(): File {
        val name = "Dead-Crossing-1.0.6_orijinal.apk"
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        throw IllegalStateException("$name must sit in the workspace root")
    }

    private fun zipBytes(apk: File, path: String): ByteArray {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(path) ?: throw IllegalStateException("$path missing from ${apk.name}")
            return zip.getInputStream(entry).use { it.readBytes() }
        }
    }

    private fun countHits(data: ByteArray, needle: ByteArray): Int {
        var count = 0
        var start = 0
        while (true) {
            val index = indexOf(data, needle, start)
            if (index < 0) return count
            count++
            start = index + 2
        }
    }

    private fun indexOf(data: ByteArray, needle: ByteArray, start: Int): Int {
        if (needle.isEmpty() || start > data.size - needle.size) return -1
        outer@ for (i in start..data.size - needle.size) {
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
