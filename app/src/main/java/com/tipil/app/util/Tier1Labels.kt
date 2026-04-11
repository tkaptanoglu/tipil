package com.tipil.app.util

/**
 * Tier-1 classification labels (Fiction / Non-Fiction and their variants).
 * These must never appear as tier-2 genre tags.
 *
 * All entries are lowercase for case-insensitive comparison.
 * Usage: `genre.lowercase() !in Tier1Labels.labels`
 */
object Tier1Labels {
    val labels: Set<String> = setOf(
        "fiction", "non-fiction", "nonfiction", "non fiction",
        "literary fiction", "general fiction", "juvenile fiction",
        "juvenile nonfiction", "juvenile non-fiction"
    )
}
