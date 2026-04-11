package com.tipil.app.data.local

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StringListConverter (Room TypeConverter).
 */
class StringListConverterTest {

    private lateinit var converter: StringListConverter

    @Before
    fun setup() {
        converter = StringListConverter()
    }

    // ───────────────────────────────────────────────────────────────
    // fromList → toList round-trip
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `round-trip with single item`() {
        val list = listOf("Sci-Fi")
        assertEquals(list, converter.toList(converter.fromList(list)))
    }

    @Test
    fun `round-trip with multiple items`() {
        val list = listOf("Sci-Fi", "Fantasy", "Adventure")
        assertEquals(list, converter.toList(converter.fromList(list)))
    }

    @Test
    fun `round-trip with empty list`() {
        val list = emptyList<String>()
        assertEquals(list, converter.toList(converter.fromList(list)))
    }

    // ───────────────────────────────────────────────────────────────
    // fromList
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fromList joins with double pipe`() {
        assertEquals("A||B||C", converter.fromList(listOf("A", "B", "C")))
    }

    @Test
    fun `fromList with empty list returns empty string`() {
        assertEquals("", converter.fromList(emptyList()))
    }

    // ───────────────────────────────────────────────────────────────
    // toList
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `toList splits on double pipe`() {
        assertEquals(listOf("A", "B", "C"), converter.toList("A||B||C"))
    }

    @Test
    fun `toList with blank string returns empty list`() {
        assertEquals(emptyList<String>(), converter.toList(""))
        assertEquals(emptyList<String>(), converter.toList("   "))
    }

    @Test
    fun `toList with single item`() {
        assertEquals(listOf("Fantasy"), converter.toList("Fantasy"))
    }

    // ───────────────────────────────────────────────────────────────
    // Edge cases
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `item containing single pipe is preserved`() {
        val list = listOf("Comics | Graphic Novels")
        assertEquals(list, converter.toList(converter.fromList(list)))
    }

    @Test
    fun `item containing special characters is preserved`() {
        val list = listOf("Sci-Fi", "Children's", "Health & Wellness")
        assertEquals(list, converter.toList(converter.fromList(list)))
    }

    // ───────────────────────────────────────────────────────────────
    // Fuzz
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fuzz - random strings survive round-trip if no double-pipe in values`() {
        val random = java.util.Random(42)
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789 -&/'"
        repeat(500) {
            val size = random.nextInt(8)
            val list = (1..size).map {
                val len = random.nextInt(20) + 1
                (1..len).map { chars[random.nextInt(chars.length)] }.joinToString("")
            }
            val serialized = converter.fromList(list)
            val deserialized = converter.toList(serialized)
            assertEquals("Round-trip failed for $list", list, deserialized)
        }
    }
}
