package com.zvpn.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class BrowserTabItem(
    val id: Int,
    var title: String,
    var url: String,
    val description: String,
    val category: String,
)

data class BookmarkItem(
    val title: String,
    val url: String,
)

data class HistoryItem(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
)



class BrowserViewModel : ViewModel() {
    val tabs = mutableStateListOf<BrowserTabItem>().apply {
        addAll(
            listOf(
                BrowserTabItem(1, "Tor Check", "https://check.torproject.org", "Verifies active Tor circuit anonymity and IP address masking.", "Security"),
                BrowserTabItem(2, "DuckDuckGo", "https://duckduckgo.com", "Zero-tracking private search engine with enhanced tracker blocking.", "Search"),
                BrowserTabItem(3, "Ahmia Search", "https://ahmia.fi", "Encrypted onion directory and secure privacy search engine.", "Onion"),
                BrowserTabItem(4, "Proton Privacy", "https://proton.me", "End-to-end encrypted private mail, calendar, and cloud storage.", "Privacy"),
                BrowserTabItem(5, "Brave Search", "https://search.brave.com", "Independent private search index with no user profiling.", "Search"),
                BrowserTabItem(6, "Tor Metrics", "https://metrics.torproject.org", "Real-time Tor network telemetry, relay status, and circuit stats.", "Analytics"),
            )
        )
    }

    var activeTabId by mutableStateOf(1)
    var canGoBack by mutableStateOf(false)
    var privacyShieldActive by mutableStateOf(true)
    var desktopMode by mutableStateOf(false)
    var blockedTrackersCount by mutableStateOf(14)

    val bookmarks = mutableStateListOf<BookmarkItem>()
    val history = mutableStateListOf<HistoryItem>()

    val activeTab: BrowserTabItem?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    fun updateCurrentUrl(newUrl: String) {
        val current = activeTab ?: return
        if (newUrl.isNotBlank() && current.url != newUrl) {
            current.url = newUrl
            history.add(0, HistoryItem(current.title.ifBlank { newUrl }, newUrl))
        }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isNotBlank() && bookmarks.none { it.url == url }) {
            bookmarks.add(BookmarkItem(title, url))
        }
    }

    fun clearBrowsingData() {
        history.clear()
        blockedTrackersCount = 0
    }
}
