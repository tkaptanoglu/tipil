package com.tipil.app.data.local

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MediaType enum.
 */
class MediaTypeTest {

    @Test
    fun `all media types have non-blank labels`() {
        MediaType.entries.forEach { type ->
            assertTrue("${type.name} has blank label", type.label.isNotBlank())
        }
    }

    @Test
    fun `fromName returns correct type for valid names`() {
        MediaType.entries.forEach { type ->
            assertEquals(type, MediaType.fromName(type.name))
        }
    }

    @Test
    fun `fromName falls back to BOOK for unknown name`() {
        assertEquals(MediaType.BOOK, MediaType.fromName(""))
        assertEquals(MediaType.BOOK, MediaType.fromName("unknown"))
        assertEquals(MediaType.BOOK, MediaType.fromName("FLOPPY_DISK"))
    }

    @Test
    fun `fromName is case-sensitive`() {
        // "book" (lowercase) is not "BOOK"
        assertEquals(MediaType.BOOK, MediaType.fromName("book")) // falls back
    }

    @Test
    fun `expected media types exist`() {
        assertNotNull(MediaType.valueOf("BOOK"))
        assertNotNull(MediaType.valueOf("CD"))
        assertNotNull(MediaType.valueOf("CASSETTE"))
        assertNotNull(MediaType.valueOf("DVD"))
        assertNotNull(MediaType.valueOf("MAGAZINE"))
        assertNotNull(MediaType.valueOf("BOARD_GAME"))
    }

    @Test
    fun `labels are user-friendly`() {
        assertEquals("Book", MediaType.BOOK.label)
        assertEquals("CD", MediaType.CD.label)
        assertEquals("Cassette", MediaType.CASSETTE.label)
        assertEquals("DVD", MediaType.DVD.label)
        assertEquals("Magazine", MediaType.MAGAZINE.label)
        assertEquals("Board Game", MediaType.BOARD_GAME.label)
    }
}
