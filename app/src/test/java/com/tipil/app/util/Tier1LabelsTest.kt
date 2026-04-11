package com.tipil.app.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Tier1Labels.
 */
class Tier1LabelsTest {

    @Test
    fun `contains fiction`() {
        assertTrue("fiction" in Tier1Labels.labels)
    }

    @Test
    fun `contains non-fiction variants`() {
        assertTrue("non-fiction" in Tier1Labels.labels)
        assertTrue("nonfiction" in Tier1Labels.labels)
        assertTrue("non fiction" in Tier1Labels.labels)
    }

    @Test
    fun `contains juvenile variants`() {
        assertTrue("juvenile fiction" in Tier1Labels.labels)
        assertTrue("juvenile nonfiction" in Tier1Labels.labels)
        assertTrue("juvenile non-fiction" in Tier1Labels.labels)
    }

    @Test
    fun `all entries are lowercase`() {
        Tier1Labels.labels.forEach { label ->
            assertEquals("Label '$label' should be lowercase", label.lowercase(), label)
        }
    }

    @Test
    fun `does not contain specific genre labels`() {
        assertFalse("romance" in Tier1Labels.labels)
        assertFalse("mystery" in Tier1Labels.labels)
        assertFalse("sci-fi" in Tier1Labels.labels)
        assertFalse("history" in Tier1Labels.labels)
    }
}
