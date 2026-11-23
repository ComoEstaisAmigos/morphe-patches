package app.deadcrossing.patches.mono

import app.adebisi.patches.shared.applyEdits
import app.adebisi.patches.shared.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DllEditsTest {
    @Test
    fun `currency lists partition the 41 ranges and do not overlap`() {
        assertEquals(2, DllEdits.CURRENCY_BODIES.size)
        assertEquals(2, DllEdits.CURRENCY_GRANT.size)
        assertEquals(35, DllEdits.CURRENCY_WRITES.size)
        assertEquals(2, DllEdits.CURRENCY_CALLERS.size)
        assertEquals(41, DllEdits.CURRENCY.size)
        assertEquals(
            DllEdits.CURRENCY_BODIES + DllEdits.CURRENCY_GRANT +
                DllEdits.CURRENCY_WRITES + DllEdits.CURRENCY_CALLERS,
            DllEdits.CURRENCY,
        )
        val ordered = DllEdits.CURRENCY.sortedBy { it.offset }
        for (i in 0 until ordered.lastIndex) {
            val a = ordered[i]
            val b = ordered[i + 1]
            assertTrue(
                a.offset + a.old.size <= b.offset,
                "0x${a.offset.toString(16)} overlaps 0x${b.offset.toString(16)}",
            )
        }
        assertFalse(DllEdits.CURRENCY_WRITES.any { it.offset == 0x140C4 })
    }

    @Test
    fun `grant expands glory with bge s and leaves gold as ldc i4`() {
        assertEquals(listOf(0x140A9, 0x140BE), DllEdits.CURRENCY_GRANT.map { it.offset })
        assertEquals("20e8030000", DllEdits.CURRENCY_GRANT[0].old.toHex())
        assertEquals("20ffe0f505", DllEdits.CURRENCY_GRANT[0].new.toHex())
        assertEquals("3c060000001680e707000472128000701f0a", DllEdits.CURRENCY_GRANT[1].old.toHex())
        assertEquals("2f0616284a000006721280007020ffe0f505", DllEdits.CURRENCY_GRANT[1].new.toHex())
        assertTrue(DllEdits.CURRENCY_GRANT[1].new.toHex().startsWith("2f06"))
        assertTrue("1f0a" in DllEdits.CURRENCY_GRANT[1].old.toHex())
        assertFalse("1f0a" in DllEdits.CURRENCY_GRANT[1].new.toHex())
    }

    @Test
    fun `caves are 16 nop then 21 byte clamp and writes are stsfld to call`() {
        for (edit in DllEdits.CURRENCY_BODIES) {
            assertEquals(37, edit.old.size)
            assertEquals("00".repeat(16), edit.new.toHex().take(32))
            assertTrue(edit.new.toHex().endsWith("2a"))
            assertTrue("20ffe0f505" in edit.new.toHex())
        }
        val gold = DllEdits.CURRENCY_WRITES.filter { it.old.toHex() == "80e7070004" }
        val merit = DllEdits.CURRENCY_WRITES.filter { it.old.toHex() == "80e8070004" }
        assertEquals(14, gold.size)
        assertEquals(21, merit.size)
        assertTrue(gold.all { it.new.toHex() == "284a000006" })
        assertTrue(merit.all { it.new.toHex() == "284b000006" })
    }

    @Test
    fun `callers pop the mission index instead of nop only`() {
        assertEquals("284a000006", DllEdits.CURRENCY_CALLERS[0].old.toHex())
        assertEquals("284b000006", DllEdits.CURRENCY_CALLERS[1].old.toHex())
        assertTrue(DllEdits.CURRENCY_CALLERS.all { it.new.toHex() == "2600000000" })
    }

    @Test
    fun `currency applyEdits matches every file-offset run`() {
        val data = ByteArray(DllEdits.CURRENCY.maxOf { it.offset + it.old.size })
        for (edit in DllEdits.CURRENCY) {
            edit.old.copyInto(data, edit.offset)
        }
        val patched = data.applyEdits(DllEdits.CURRENCY)
        for (edit in DllEdits.CURRENCY) {
            assertTrue(
                patched.copyOfRange(edit.offset, edit.offset + edit.new.size).contentEquals(edit.new),
            )
        }
        assertFalse(data.contentEquals(patched))
    }
}
