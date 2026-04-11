package com.tipil.app.util

/**
 * ISBN-10 and ISBN-13 checksum validation.
 * Extracted from ScannerScreen for testability.
 */
object IsbnValidator {

    fun isValid(value: String): Boolean {
        if (!value.all { it.isDigit() }) return false
        return when (value.length) {
            13 -> isValidIsbn13(value)
            10 -> isValidIsbn10(value)
            else -> false
        }
    }

    fun isValidIsbn13(isbn: String): Boolean {
        if (isbn.length != 13 || !isbn.all { it.isDigit() }) return false
        val sum = isbn.mapIndexed { i, c ->
            val digit = c.digitToInt()
            if (i % 2 == 0) digit else digit * 3
        }.sum()
        return sum % 10 == 0
    }

    fun isValidIsbn10(isbn: String): Boolean {
        if (isbn.length != 10 || !isbn.all { it.isDigit() }) return false
        val sum = isbn.mapIndexed { i, c ->
            c.digitToInt() * (10 - i)
        }.sum()
        return sum % 11 == 0
    }
}
