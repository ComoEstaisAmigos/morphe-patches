package app.deadcrossing.patches.nativelib

import app.adebisi.patches.shared.applyEdits
import app.adebisi.patches.shared.toHex
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What would have caught a TEXTREL-only rewrite: dropping DT_TEXTREL while
 * libmain and libunity still had R_ARM_ABS32 of __cxa_atexit sitting in RX .text.
 *
 * The five-run Origins pattern is not enough on those two libraries. Applying
 * only those five leaves a type-2 text reloc; the extra Thumb `bl plt` plus
 * R_ARM_NONE retype leaves none.
 */
class NativeEditsEval {
    @Test
    fun `five-run TEXTREL still leaves ABS32 in main and unity text`() {
        val apk = originalApk()
        val main = zipBytes(apk, NativeEdits.MAIN)
        val unity = zipBytes(apk, NativeEdits.UNITY_REAL)

        val mainFive = main.applyEdits(NativeEdits.MAIN_TEXTREL.take(5))
        val unityFive = unity.applyEdits(NativeEdits.UNITY_TEXTREL.take(5))
        assertEquals(listOf(2), textRelocTypes(mainFive), "libmain five-run")
        assertEquals(listOf(2), textRelocTypes(unityFive), "libunity five-run")

        assertTrue(textRelocTypes(main.applyEdits(NativeEdits.MAIN_TEXTREL)).isEmpty())
        assertTrue(textRelocTypes(unity.applyEdits(NativeEdits.UNITY_TEXTREL)).isEmpty())
        assertTrue(textRelocTypes(zipBytes(apk, NativeEdits.FXS1).applyEdits(NativeEdits.FXS1_TEXTREL)).isEmpty())
        assertTrue(
            textRelocTypes(zipBytes(apk, NativeEdits.MONO_REAL).applyEdits(NativeEdits.MONO_TEXTREL)).isEmpty(),
        )
    }

    @Test
    fun `stock libunity still has the HWStats URL`() {
        val unity = zipBytes(originalApk(), NativeEdits.UNITY_REAL)
        val off = NativeEdits.UNITY_HWSTATS[0].offset
        val old = NativeEdits.UNITY_HWSTATS[0].old
        assertTrue(unity.copyOfRange(off, off + old.size).contentEquals(old))
        val patched = unity.applyEdits(NativeEdits.UNITY_TEXTREL + NativeEdits.UNITY_HWSTATS)
        assertTrue(patched.copyOfRange(off, off + old.size).contentEquals(ByteArray(old.size)))
        assertTrue(textRelocTypes(patched).isEmpty())
    }

    @Test
    fun `atexit rewrite is a Thumb bl to plt not a nop`() {
        assertEquals("024b9847", NativeEdits.MAIN_TEXTREL[5].old.toHex())
        assertEquals("fff7a7ff", NativeEdits.MAIN_TEXTREL[5].new.toHex())
        assertEquals("024b9847", NativeEdits.UNITY_TEXTREL[5].old.toHex())
        assertEquals("fff753fb", NativeEdits.UNITY_TEXTREL[5].new.toHex())
        assertTrue("00207047" !in NativeEdits.MAIN_TEXTREL[5].new.toHex())
        assertTrue("00207047" !in NativeEdits.UNITY_TEXTREL[5].new.toHex())
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

    private fun textRelocTypes(elf: ByteArray): List<Int> {
        fun u16(o: Int) = (elf[o].toInt() and 0xff) or ((elf[o + 1].toInt() and 0xff) shl 8)
        fun u32(o: Int): Long {
            var v = 0L
            for (i in 0 until 4) v = v or ((elf[o + i].toLong() and 0xff) shl (8 * i))
            return v
        }
        val phoff = u32(0x1C).toInt()
        val phentsize = u16(0x2A)
        val phnum = u16(0x2C)
        val loads = ArrayList<LongArray>()
        var dynOff = -1
        var dynSz = 0
        for (i in 0 until phnum) {
            val p = phoff + i * phentsize
            val type = u32(p).toInt()
            if (type == 1) {
                loads.add(longArrayOf(u32(p + 8), u32(p + 16), u32(p + 20), u32(p + 24), u32(p + 4)))
            }
            if (type == 2) {
                dynOff = u32(p + 4).toInt()
                dynSz = u32(p + 16).toInt()
            }
        }
        var rel = -1L
        var relsz = 0L
        var relent = 8L
        var o = dynOff
        while (o in 0 until dynOff + dynSz) {
            val tag = u32(o)
            val value = u32(o + 4)
            if (tag == 0L) break
            if (tag == 17L) rel = value
            if (tag == 18L) relsz = value
            if (tag == 19L) relent = value
            o += 8
        }
        fun vaToOff(va: Long): Int {
            for (seg in loads) {
                val vaddr = seg[0]
                val filesz = seg[1]
                val offset = seg[4]
                if (va >= vaddr && va < vaddr + filesz) return (offset + (va - vaddr)).toInt()
            }
            throw IllegalStateException("vaddr 0x${va.toString(16)} outside PT_LOAD")
        }
        fun inX(va: Long): Boolean {
            for (seg in loads) {
                val vaddr = seg[0]
                val memsz = seg[2]
                val flags = seg[3]
                if (va >= vaddr && va < vaddr + memsz && (flags and 1L) != 0L) return true
            }
            return false
        }
        val off = vaToOff(rel)
        val n = (relsz / relent).toInt()
        val types = ArrayList<Int>()
        for (i in 0 until n) {
            val entry = off + i * relent.toInt()
            val rOffset = u32(entry)
            val rType = (u32(entry + 4) and 0xff).toInt()
            if (rType != 0 && inX(rOffset)) types.add(rType)
        }
        return types
    }
}
