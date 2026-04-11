package com.tipil.app.util

import com.tipil.app.data.remote.VolumeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-tier classification system:
 *   Tier 1: Fiction vs Non-Fiction (determined by isFiction())
 *   Tier 2: Specific genre tags like Sci-Fi, History, Romance, etc. (determined by classify())
 *
 * Tier-2 genres never include "Fiction" or "Non-Fiction" — that's tier 1's job.
 */
@Singleton
class GenreClassifier @Inject constructor() {

    private val fictionKeywords = setOf(
        "fiction", "novel", "fantasy", "science fiction", "mystery",
        "thriller", "romance", "horror", "adventure", "literary fiction",
        "dystopian", "fairy tale", "fable", "comic", "graphic novel",
        "manga", "young adult fiction", "children's fiction"
    )

    private val nonFictionKeywords = setOf(
        "biography", "autobiography", "memoir", "history", "science",
        "technology", "self-help", "business", "economics", "philosophy",
        "psychology", "religion", "politics", "travel", "cooking",
        "health", "education", "reference", "true crime", "journalism",
        "mathematics", "engineering", "medicine", "law"
    )

    // Tier-2 genre map — no "Fiction" or "Non-Fiction" entries
    private val genreMap = mapOf(
        "science fiction" to "Sci-Fi",
        "fantasy" to "Fantasy",
        "mystery" to "Mystery",
        "thriller" to "Thriller",
        "romance" to "Romance",
        "horror" to "Horror",
        "adventure" to "Adventure",
        "dystopian" to "Dystopian",
        "young adult" to "Young Adult",
        "children" to "Children's",
        "comic" to "Comics & Graphic Novels",
        "graphic novel" to "Comics & Graphic Novels",
        "manga" to "Manga",
        "biography" to "Biography",
        "autobiography" to "Autobiography",
        "memoir" to "Memoir",
        "history" to "History",
        "science" to "Science",
        "technology" to "Technology",
        "computers" to "Technology",
        "self-help" to "Self-Help",
        "business" to "Business",
        "economics" to "Economics",
        "philosophy" to "Philosophy",
        "psychology" to "Psychology",
        "religion" to "Religion",
        "spirituality" to "Spirituality",
        "politics" to "Politics",
        "political" to "Politics",
        "travel" to "Travel",
        "cooking" to "Cooking",
        "health" to "Health & Wellness",
        "fitness" to "Health & Wellness",
        "education" to "Education",
        "poetry" to "Poetry",
        "drama" to "Drama",
        "art" to "Art",
        "music" to "Music",
        "nature" to "Nature",
        "true crime" to "True Crime",
        "humor" to "Humor",
        "sports" to "Sports",
        "mathematics" to "Mathematics",
        "engineering" to "Engineering",
        "medicine" to "Medicine",
        "law" to "Law"
    )

    // Tier-1 labels that must never leak into tier-2 genre lists (lowercase for comparison)
    private val tier1Labels = setOf(
        "fiction", "non-fiction", "nonfiction", "non fiction",
        "literary fiction", "general fiction", "juvenile fiction",
        "juvenile nonfiction", "juvenile non-fiction"
    )

    /**
     * Tier 2: Returns specific genre tags (e.g. Sci-Fi, History, Romance).
     * Never returns "Fiction" or "Non-Fiction" — that belongs to tier 1.
     */
    fun classify(volumeInfo: VolumeInfo): List<String> {
        val categories = volumeInfo.categories ?: emptyList()
        val mainCategory = volumeInfo.mainCategory ?: ""
        val description = volumeInfo.description ?: ""

        val allText = (categories + mainCategory).joinToString(" ").lowercase()
        val descLower = description.lowercase()

        val genres = mutableSetOf<String>()

        for ((keyword, genre) in genreMap) {
            if (keyword in allText || keyword in descLower) {
                genres.add(genre)
            }
        }

        // If no genres matched, try to extract from the raw categories
        if (genres.isEmpty()) {
            categories.forEach { category ->
                val parts = category.split("/", "&").map { it.trim() }
                parts.forEach { part ->
                    if (part.isNotBlank()) {
                        genres.add(part.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        // Strip out any tier-1 labels that may have slipped in (case-insensitive)
        genres.removeAll { it.lowercase() in tier1Labels }

        return genres.toList().take(5)
    }

    /**
     * Tier 1: Determines whether a book is Fiction or Non-Fiction.
     */
    fun isFiction(volumeInfo: VolumeInfo): Boolean {
        val categories = volumeInfo.categories ?: emptyList()
        val mainCategory = volumeInfo.mainCategory ?: ""
        val allText = (categories + mainCategory).joinToString(" ").lowercase()

        val fictionScore = fictionKeywords.count { it in allText }
        val nonFictionScore = nonFictionKeywords.count { it in allText }

        return fictionScore >= nonFictionScore
    }
}
