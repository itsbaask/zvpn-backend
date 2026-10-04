package dev.cluvex.zedsecure.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class BrowserTorState { Disconnected, Connecting, Connected }

class BrowserTabState(val id: Int, url: String = "") {
    var url by mutableStateOf(url)
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
}

/** Commands the UI sends to the platform web view of a tab. */
interface WebHandle {
    fun back()
    fun forward()
    fun reload()
    fun stop()
    fun load(url: String)
    fun clearData()
}

data class Bookmark(val title: String, val url: String)
data class HistoryEntry(val title: String, val url: String)

class BrowserController : ViewModel() {
    val tabs = mutableStateListOf(BrowserTabState(1))
    var activeId by mutableIntStateOf(1)
    var totalBlocked by mutableIntStateOf(0)

    /** Independent Tor connection state for Browser. */
    var torState by mutableStateOf(BrowserTorState.Disconnected)
    var torProgress by mutableIntStateOf(0)
    var triggerToggleTor by mutableIntStateOf(0)

    /** null = not checked yet, true = a real request through the proxy returned IsTor=true. */
    var torVerified by mutableStateOf<Boolean?>(null)

    val bookmarks = mutableStateListOf<Bookmark>()
    val history = mutableStateListOf<HistoryEntry>()

    private var nextId = 2
    private val handles = mutableMapOf<Int, WebHandle>()

    val active: BrowserTabState get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val isBookmarked: Boolean get() = bookmarks.any { it.url == active.url }

    fun toggleTor() {
        triggerToggleTor++
    }

    fun attach(id: Int, handle: WebHandle?) {
        if (handle == null) handles.remove(id) else handles[id] = handle
    }

    fun open(raw: String) {
        val u = normalizeUrl(raw)
        if (u.isBlank()) return
        val t = active
        t.url = u
        handles[t.id]?.load(u)
    }

    fun home() { active.url = ""; active.loading = false; active.canGoBack = false; active.canGoForward = false }
    fun back() { val t = active; val h = handles[t.id]; if (h != null && t.canGoBack) h.back() else home() }
    fun forward() { handles[active.id]?.forward() }
    fun reloadOrStop() { val t = active; handles[t.id]?.let { if (t.loading) it.stop() else it.reload() } }

    fun newTab() { tabs.add(BrowserTabState(nextId)); activeId = nextId; nextId++ }
    fun select(id: Int) { activeId = id }
    fun close(id: Int) {
        if (tabs.size == 1) { home(); return }
        val wasActive = id == activeId
        tabs.removeAll { it.id == id }
        if (wasActive) activeId = tabs.last().id
    }

    fun toggleBookmark() {
        val t = active
        if (t.url.isBlank()) return
        if (!bookmarks.removeAll { it.url == t.url }) bookmarks.add(0, Bookmark(t.title.ifBlank { hostOf(t.url) }, t.url))
    }

    fun recordVisit(url: String, title: String?) {
        if (url.isBlank() || history.firstOrNull()?.url == url) return
        history.add(0, HistoryEntry(title.orEmpty().ifBlank { hostOf(url) }, url))
        while (history.size > 200) history.removeAt(history.lastIndex)
    }

    fun clearAll() {
        handles.values.forEach { it.clearData() }
        history.clear()
        totalBlocked = 0
        tabs.forEach { it.blocked = 0 }
    }

    override fun onCleared() { handles.clear() }
}

private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("http://").substringBefore('/')

private fun normalizeUrl(raw: String): String {
    val s = raw.trim()
    return when {
        s.isEmpty() -> ""
        s.startsWith("http://") || s.startsWith("https://") -> s
        '.' in s && ' ' !in s -> "https://$s"
        else -> "https://duckduckgo.com/?q=" + s.replace(' ', '+')
    }
}
