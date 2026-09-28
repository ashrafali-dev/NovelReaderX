package com.ashraf.novelreader

import android.net.Uri

enum class NavigationMode {
    DOM,
    WEBNOVEL_CATALOG
}

data class NovelSiteProfile(
    val host: String,
    val contentSelectors: List<String>,
    val titleSelectors: List<String>,
    val nextSelectors: List<String>,
    val prevSelectors: List<String>,
    val navigationMode: NavigationMode = NavigationMode.DOM
)

object SiteProfiles {
    private val generic = NovelSiteProfile(
        host = "*",
        contentSelectors = listOf(
            "#chapter-content", ".chapter-content", ".chapter_content", "#chr-content", ".chr-c",
            ".reading-content", ".text-left", "#content", ".entry-content", ".cha-content",
            ".cha-words", ".chapter-body", ".novel_content", ".j_readContent", ".txt",
            "#chaptercontent", ".chapter-c", "#article", ".article-content", ".content", "article"
        ),
        titleSelectors = listOf(
            ".chapter-title", ".chr-title", "#chapter-heading", ".j_chapterName",
            ".chapter-name", "h1", "h2"
        ),
        nextSelectors = listOf(
            "#next", "[data-testid='next']", "[aria-label*='Next Chapter' i]",
            "[title*='Next Chapter' i]", "a[rel='next']", "button[title*='Next' i]"
        ),
        prevSelectors = listOf(
            "#prev", "[data-testid='prev']", "[aria-label*='Previous Chapter' i]",
            "[title*='Previous Chapter' i]", "a[rel='prev']", "button[title*='Previous' i]"
        )
    )

    private val webNovel = generic.copy(
        host = "webnovel.com",
        contentSelectors = listOf(
            ".j_readContent", ".cha-content", ".chapter-content", "#chapter-content",
            ".chapter-body", ".chapter-c", ".txt", "#chaptercontent", ".content", "article"
        ),
        titleSelectors = listOf(
            ".chapter-title", ".j_chapterName", ".chapter-name", ".chr-title",
            "#chapter-heading", "h1", "h2"
        ),
        nextSelectors = listOf(
            "#next", "[id='next']", "[data-testid='next']",
            "[aria-label*='Next Chapter' i]", "[title*='Next Chapter' i]",
            "mov-button#next", "a[rel='next']"
        ),
        prevSelectors = listOf(
            "#prev", "[id='prev']", "[data-testid='prev']",
            "[aria-label*='Previous Chapter' i]", "[title*='Previous Chapter' i]",
            "mov-button#prev", "a[rel='prev']"
        ),
        navigationMode = NavigationMode.WEBNOVEL_CATALOG
    )

    fun forUrl(url: String): NovelSiteProfile {
        val host = runCatching { Uri.parse(url).host?.lowercase().orEmpty() }.getOrDefault("")
        return if (host == "webnovel.com" || host.endsWith(".webnovel.com")) webNovel else generic
    }
}
