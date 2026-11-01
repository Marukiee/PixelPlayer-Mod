package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchTextNormalizerTest {

    @Test
    fun fold_removesSwedishDiacritics() {
        assertThat(SearchTextNormalizer.fold("Håkan Hellström")).isEqualTo("hakan hellstrom")
        assertThat(SearchTextNormalizer.fold("Kärlek Är Ett Brev")).isEqualTo("karlek ar ett brev")
        assertThat(SearchTextNormalizer.fold("ÅÄÖ")).isEqualTo("aao")
    }

    @Test
    fun fold_mapsLettersWithoutDecomposition() {
        assertThat(SearchTextNormalizer.fold("Mø")).isEqualTo("mo")
        assertThat(SearchTextNormalizer.fold("Sigur Rós Ágætis byrjun")).isEqualTo("sigur ros agaetis byrjun")
        assertThat(SearchTextNormalizer.fold("Straße")).isEqualTo("strasse")
    }

    @Test
    fun fold_dropsApostrophesAndSplitsOtherPunctuation() {
        assertThat(SearchTextNormalizer.fold("Don't Stop")).isEqualTo("dont stop")
        assertThat(SearchTextNormalizer.fold("Don’t")).isEqualTo("dont")
        assertThat(SearchTextNormalizer.fold("AC/DC - Back In Black")).isEqualTo("ac dc back in black")
    }

    @Test
    fun containsAll_matchesEveryTokenInAnyOrder() {
        val folded = SearchTextNormalizer.fold("Känn ingen sorg för mig Göteborg Håkan Hellström")
        assertThat(SearchTextNormalizer.containsAll(folded, SearchTextNormalizer.tokens("hellstrom goteborg"))).isTrue()
        assertThat(SearchTextNormalizer.containsAll(folded, SearchTextNormalizer.tokens("Göteborg"))).isTrue()
        assertThat(SearchTextNormalizer.containsAll(folded, SearchTextNormalizer.tokens("hellstrom stockholm"))).isFalse()
    }

    @Test
    fun tokens_ignoresBlankInput() {
        assertThat(SearchTextNormalizer.tokens("   ")).isEmpty()
        assertThat(SearchTextNormalizer.tokens("'")).isEmpty()
        assertThat(SearchTextNormalizer.tokens(null)).isEmpty()
    }
}
