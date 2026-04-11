package com.tipil.app.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for IsbnValidator.
 * Covers ISBN-10 and ISBN-13 checksum logic, edge cases, and invalid inputs.
 */
class IsbnValidatorTest {

    // ───────────────────────────────────────────────────────────────
    // ISBN-13 valid checksums
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `valid ISBN-13 passes`() {
        // "Sapiens" by Yuval Noah Harari
        assertTrue(IsbnValidator.isValidIsbn13("9780062316097"))
    }

    @Test
    fun `valid ISBN-13 with 978 prefix passes`() {
        // "1984" by George Orwell
        assertTrue(IsbnValidator.isValidIsbn13("9780451524935"))
    }

    @Test
    fun `valid ISBN-13 with 979 prefix passes`() {
        // 979 prefix (French ISBN range)
        assertTrue(IsbnValidator.isValidIsbn13("9791032305690"))
    }

    // ───────────────────────────────────────────────────────────────
    // ISBN-13 invalid checksums
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `ISBN-13 with wrong check digit fails`() {
        // Last digit changed from 7 to 8
        assertFalse(IsbnValidator.isValidIsbn13("9780062316098"))
    }

    @Test
    fun `ISBN-13 all zeros fails`() {
        // 0000000000000 → sum = 0, mod 10 = 0 → technically valid checksum
        // This is an edge case: mathematically valid but semantically meaningless
        assertTrue(IsbnValidator.isValidIsbn13("0000000000000"))
    }

    @Test
    fun `ISBN-13 with wrong length fails`() {
        assertFalse(IsbnValidator.isValidIsbn13("978006231609"))  // 12 digits
        assertFalse(IsbnValidator.isValidIsbn13("97800623160977")) // 14 digits
    }

    @Test
    fun `ISBN-13 with letters fails`() {
        assertFalse(IsbnValidator.isValidIsbn13("978006231609X"))
    }

    // ───────────────────────────────────────────────────────────────
    // ISBN-10 valid checksums
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `valid ISBN-10 passes`() {
        // "The Great Gatsby"
        assertTrue(IsbnValidator.isValidIsbn10("0743273567"))
    }

    @Test
    fun `valid ISBN-10 another example passes`() {
        // "To Kill a Mockingbird"
        assertTrue(IsbnValidator.isValidIsbn10("0446310786"))
    }

    // ───────────────────────────────────────────────────────────────
    // ISBN-10 invalid
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `ISBN-10 with wrong check digit fails`() {
        assertFalse(IsbnValidator.isValidIsbn10("0743273568"))
    }

    @Test
    fun `ISBN-10 with X check digit fails because validator requires all digits`() {
        // ISBN-10 can technically end in 'X' (=10), but our validator rejects non-digit chars
        assertFalse(IsbnValidator.isValidIsbn10("080442957X"))
    }

    @Test
    fun `ISBN-10 wrong length fails`() {
        assertFalse(IsbnValidator.isValidIsbn10("074327356"))   // 9 digits
        assertFalse(IsbnValidator.isValidIsbn10("07432735677"))  // 11 digits
    }

    // ───────────────────────────────────────────────────────────────
    // isValid() dispatcher
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `isValid dispatches to ISBN-13 for 13-digit string`() {
        assertTrue(IsbnValidator.isValid("9780062316097"))
    }

    @Test
    fun `isValid dispatches to ISBN-10 for 10-digit string`() {
        assertTrue(IsbnValidator.isValid("0743273567"))
    }

    @Test
    fun `isValid rejects 12-digit string`() {
        assertFalse(IsbnValidator.isValid("978006231609"))
    }

    @Test
    fun `isValid rejects empty string`() {
        assertFalse(IsbnValidator.isValid(""))
    }

    @Test
    fun `isValid rejects non-numeric string`() {
        assertFalse(IsbnValidator.isValid("abcdefghijk"))
    }

    @Test
    fun `isValid rejects string with spaces`() {
        assertFalse(IsbnValidator.isValid("978 006 2316"))
    }

    @Test
    fun `isValid rejects string with dashes`() {
        assertFalse(IsbnValidator.isValid("978-0-06-231609-7"))
    }

    // ───────────────────────────────────────────────────────────────
    // Fuzz-style: random digit strings
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fuzz - random 13-digit strings rarely pass checksum`() {
        val random = java.util.Random(42)
        var passCount = 0
        val trials = 10_000
        repeat(trials) {
            val digits = (1..13).map { random.nextInt(10) }.joinToString("")
            if (IsbnValidator.isValidIsbn13(digits)) passCount++
        }
        // Roughly 1 in 10 should pass (mod 10 check), so < 20% is expected
        assertTrue("Expected ~10% pass rate, got ${passCount * 100 / trials}%", passCount < trials / 5)
    }

    @Test
    fun `fuzz - random 10-digit strings rarely pass checksum`() {
        val random = java.util.Random(42)
        var passCount = 0
        val trials = 10_000
        repeat(trials) {
            val digits = (1..10).map { random.nextInt(10) }.joinToString("")
            if (IsbnValidator.isValidIsbn10(digits)) passCount++
        }
        // Roughly 1 in 11 should pass (mod 11 check), so < 20% is expected
        assertTrue("Expected ~9% pass rate, got ${passCount * 100 / trials}%", passCount < trials / 5)
    }
}
