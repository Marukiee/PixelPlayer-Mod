package com.theveloper.pixelplay.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for search so diacritics and punctuation never have to be typed:
 * "Håkan Hellström" and "hakan hellstrom" fold to the same string, and "dont" finds "Don't".
 */
object SearchTextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val APOSTROPHES = Regex("['`´‘’ʼ]")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

    // Letters that NFD does not split into a base letter plus a combining mark.
    private val SPECIAL_LETTERS = mapOf(
        'ø' to "o",
        'æ' to "ae",
        'œ' to "oe",
        'ß' to "ss",
        'ł' to "l",
        'đ' to "d",
        'ð' to "d",
        'þ' to "th",
        'ı' to "i",
        'ħ' to "h",
        'ŧ' to "t"
    )

    /** Lowercases and removes diacritics, but keeps punctuation and spacing. */
    fun stripDiacritics(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        return buildString(decomposed.length) {
            for (char in decomposed) {
                val replacement = SPECIAL_LETTERS[char]
                if (replacement != null) append(replacement) else append(char)
            }
        }
    }

    /** [stripDiacritics] plus: apostrophes dropped, other punctuation turned into single spaces. */
    fun fold(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return stripDiacritics(text)
            .replace(APOSTROPHES, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
    }

    fun tokens(query: String?): List<String> =
        fold(query).split(' ').filter { it.isNotEmpty() }

    /** True when every token occurs somewhere in [folded] (which must already be folded). */
    fun containsAll(folded: String, tokens: List<String>): Boolean =
        tokens.all { folded.contains(it) }
}
