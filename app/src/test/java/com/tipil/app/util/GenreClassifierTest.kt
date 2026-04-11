package com.tipil.app.util

import com.tipil.app.data.remote.VolumeInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GenreClassifier.
 * Covers tier-1 (fiction/non-fiction) and tier-2 (genre tags) classification.
 */
class GenreClassifierTest {

    private lateinit var classifier: GenreClassifier

    @Before
    fun setup() {
        classifier = GenreClassifier()
    }

    // ───────────────────────────────────────────────────────────────
    // Tier 1: isFiction
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fiction category yields isFiction true`() {
        val info = VolumeInfo(categories = listOf("Fiction"))
        assertTrue(classifier.isFiction(info))
    }

    @Test
    fun `non-fiction category yields isFiction false`() {
        val info = VolumeInfo(categories = listOf("Biography", "History"))
        assertFalse(classifier.isFiction(info))
    }

    @Test
    fun `science fiction is fiction`() {
        val info = VolumeInfo(categories = listOf("Science Fiction"))
        assertTrue(classifier.isFiction(info))
    }

    @Test
    fun `empty categories defaults to fiction`() {
        val info = VolumeInfo()
        assertTrue(classifier.isFiction(info))
    }

    @Test
    fun `mainCategory alone determines fiction`() {
        val info = VolumeInfo(mainCategory = "Fantasy")
        assertTrue(classifier.isFiction(info))
    }

    @Test
    fun `mainCategory alone determines non-fiction`() {
        val info = VolumeInfo(mainCategory = "Mathematics")
        assertFalse(classifier.isFiction(info))
    }

    @Test
    fun `mixed categories with more non-fiction keywords yields non-fiction`() {
        val info = VolumeInfo(categories = listOf("History", "Biography", "Novel"))
        // 2 non-fiction (history, biography) vs 1 fiction (novel) → non-fiction
        assertFalse(classifier.isFiction(info))
    }

    @Test
    fun `tie between fiction and non-fiction defaults to fiction`() {
        val info = VolumeInfo(categories = listOf("Novel", "History"))
        // 1 fiction (novel) vs 1 non-fiction (history) → tie → fiction
        assertTrue(classifier.isFiction(info))
    }

    // ───────────────────────────────────────────────────────────────
    // Tier 1: word-boundary accuracy
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `art keyword does not match partial words like party`() {
        // "art" should not match inside "party" or "startup"
        val info = VolumeInfo(categories = listOf("Party Planning / Startup Culture"))
        // No fiction or non-fiction signals → 0-0 tie → fiction
        assertTrue(classifier.isFiction(info))
        // And genre should not include "Art"
        val genres = classifier.classify(info)
        assertFalse("Art should not appear from 'party'", genres.contains("Art"))
    }

    @Test
    fun `science keyword does not match science fiction`() {
        // "science fiction" has both "science" (non-fiction) and "science fiction" (fiction),
        // but the overall classification with just this category should be fiction
        val info = VolumeInfo(categories = listOf("Science Fiction"))
        assertTrue(classifier.isFiction(info))
    }

    // ───────────────────────────────────────────────────────────────
    // Tier 2: classify (genre tags)
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `classify returns Sci-Fi for science fiction category`() {
        val info = VolumeInfo(categories = listOf("Science Fiction"))
        val genres = classifier.classify(info)
        assertTrue(genres.contains("Sci-Fi"))
    }

    @Test
    fun `classify returns multiple genres`() {
        val info = VolumeInfo(
            categories = listOf("Mystery", "Thriller"),
            description = "A thrilling mystery adventure"
        )
        val genres = classifier.classify(info)
        assertTrue(genres.contains("Mystery"))
        assertTrue(genres.contains("Thriller"))
    }

    @Test
    fun `classify never returns tier-1 labels`() {
        val info = VolumeInfo(categories = listOf("Fiction", "Romance"))
        val genres = classifier.classify(info)
        assertFalse("Fiction must not appear in tier-2 genres", genres.any { it.lowercase() in Tier1Labels.labels })
        assertTrue(genres.contains("Romance"))
    }

    @Test
    fun `classify caps at 5 genres`() {
        val info = VolumeInfo(
            categories = listOf("Mystery", "Thriller", "Horror", "Adventure", "Romance", "Fantasy"),
            description = "A fantasy horror mystery thriller adventure romance drama"
        )
        val genres = classifier.classify(info)
        assertTrue("Should have at most 5 genres, got ${genres.size}", genres.size <= 5)
    }

    @Test
    fun `classify falls back to raw categories when no keywords match`() {
        val info = VolumeInfo(categories = listOf("Underwater Basket Weaving"))
        val genres = classifier.classify(info)
        assertTrue("Should fall back to raw category", genres.contains("Underwater Basket Weaving"))
    }

    @Test
    fun `classify splits raw categories on slash`() {
        val info = VolumeInfo(categories = listOf("Cooking / Regional & Ethnic"))
        val genres = classifier.classify(info)
        // "cooking" matches the keyword map → "Cooking"
        assertTrue(genres.contains("Cooking"))
    }

    @Test
    fun `classify with empty VolumeInfo returns empty list`() {
        val info = VolumeInfo()
        val genres = classifier.classify(info)
        assertTrue(genres.isEmpty())
    }

    @Test
    fun `classify uses description when categories are empty`() {
        val info = VolumeInfo(description = "A gripping thriller set in Victorian London")
        val genres = classifier.classify(info)
        assertTrue(genres.contains("Thriller"))
    }

    // ───────────────────────────────────────────────────────────────
    // Tier 2: genre mapping correctness
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `computers maps to Technology`() {
        val info = VolumeInfo(categories = listOf("Computers"))
        assertTrue(classifier.classify(info).contains("Technology"))
    }

    @Test
    fun `political maps to Politics`() {
        val info = VolumeInfo(categories = listOf("Political Science"))
        assertTrue(classifier.classify(info).contains("Politics"))
    }

    @Test
    fun `graphic novel maps to Comics and Graphic Novels`() {
        val info = VolumeInfo(categories = listOf("Graphic Novel"))
        assertTrue(classifier.classify(info).contains("Comics & Graphic Novels"))
    }

    // ───────────────────────────────────────────────────────────────
    // Fuzz: random description text
    // ───────────────────────────────────────────────────────────────

    @Test
    fun `fuzz - classify never crashes on random input`() {
        val random = java.util.Random(99)
        val chars = "abcdefghijklmnopqrstuvwxyz /&0123456789"
        repeat(1_000) {
            val len = random.nextInt(200)
            val text = (1..len).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val info = VolumeInfo(categories = listOf(text), description = text)
            // Should not throw
            classifier.classify(info)
            classifier.isFiction(info)
        }
    }

    @Test
    fun `fuzz - classify never returns tier-1 labels on any input`() {
        val inputs = listOf(
            "Fiction / Literary Fiction",
            "Juvenile Fiction",
            "Non-Fiction / General",
            "Juvenile Nonfiction / Science",
            "fiction fiction fiction"
        )
        inputs.forEach { input ->
            val info = VolumeInfo(categories = listOf(input))
            val genres = classifier.classify(info)
            genres.forEach { genre ->
                assertFalse(
                    "Tier-1 label '$genre' leaked for input '$input'",
                    genre.lowercase() in Tier1Labels.labels
                )
            }
        }
    }
}
