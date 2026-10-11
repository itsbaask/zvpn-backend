package com.zvpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zvpn.data.config.ConfigRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(FlowPreview::class)
class ServersViewModel(
    private val configRepo: ConfigRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val rawServersState = MutableStateFlow<List<PublicServer>>(emptyList())
    private val searchQueryState = MutableStateFlow("")
    private val selectedTierState = MutableStateFlow(ServerTier.Free)
    private val selectedProtocolState = MutableStateFlow(ProtocolFilter.All)
    private val currentSortState = MutableStateFlow(SortOption.Recommended)
    private val favoriteIdsState = MutableStateFlow<Set<String>>(emptySet())
    private val pingingIdsState = MutableStateFlow<Set<String>>(emptySet())
    private val isLoadingState = MutableStateFlow(false)

    init {
        configRepo?.let { repo ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repo.syncSystemBackendServers() }
            }
            viewModelScope.launch(dispatcher) {
                repo.profiles.collect { profiles ->
                    val mapped = profiles.map { p ->
                        PublicServer(
                            id = p.id,
                            displayName = p.name,
                            countryCode = p.countryCode ?: "US",
                            countryName = p.name.substringBefore(" -").trim(),
                            flag = "🌐",
                            protocolLabel = p.protocol.uppercase(),
                            speedMbps = 25.0f,
                            latencyMs = p.lastPingMs,
                            tier = ServerTier.Free,
                            continent = "Global",
                        )
                    }
                    rawServersState.value = mapped
                }
            }
        }
    }

    private data class FilterTuple(val query: String, val tier: ServerTier, val protocol: ProtocolFilter)
    private data class SortTuple(val sort: SortOption, val favorites: Set<String>, val pinging: Set<String>)
    private data class DataTuple(val servers: List<PublicServer>, val loading: Boolean)

    val uiState: StateFlow<ServersUiState> = combine(
        combine(searchQueryState.debounce(250), selectedTierState, selectedProtocolState) { q, t, p -> FilterTuple(q, t, p) },
        combine(currentSortState, favoriteIdsState, pingingIdsState) { s, f, pg -> SortTuple(s, f, pg) },
        combine(rawServersState, isLoadingState) { srv, load -> DataTuple(srv, load) }
    ) { filters, sorting, data ->
        val query = filters.query
        val tier = filters.tier
        val protocol = filters.protocol
        val sort = sorting.sort
        val favorites = sorting.favorites
        val pinging = sorting.pinging
        val servers = data.servers
        val loading = data.loading

        val normalizedQuery = query.trim().lowercase()

        val filtered = servers.filter { s ->
            val matchTier = s.tier == tier
            val matchProto = protocol == ProtocolFilter.All || s.protocolLabel.equals(protocol.label, ignoreCase = true)
            val matchQuery = normalizedQuery.isEmpty() ||
                    s.countryName.lowercase().contains(normalizedQuery) ||
                    s.protocolLabel.lowercase().contains(normalizedQuery) ||
                    s.displayName.lowercase().contains(normalizedQuery)

            matchTier && matchProto && matchQuery
        }

        val sorted = when (sort) {
            SortOption.Speed -> filtered.sortedByDescending { it.speedMbps }
            SortOption.Ping -> filtered.sortedBy { it.latencyMs ?: Int.MAX_VALUE }
            SortOption.Name -> filtered.sortedBy { it.countryName }
            SortOption.Favorites -> filtered.sortedByDescending { favorites.contains(it.id) }
            SortOption.Recommended -> filtered.sortedWith(
                compareByDescending<PublicServer> { favorites.contains(it.id) }
                    .thenBy { it.latencyMs ?: Int.MAX_VALUE }
                    .thenByDescending { it.speedMbps }
            )
        }

        val pagedServers = sorted.take(300)

        ServersUiState(
            searchQuery = query,
            selectedTier = tier,
            selectedProtocol = protocol,
            currentSort = sort,
            favoriteIds = favorites,
            pingingIds = pinging,
            servers = pagedServers,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ServersUiState()
    )

    fun onSearchChange(query: String) {
        searchQueryState.value = query
    }

    fun onTierSelected(tier: ServerTier) {
        selectedTierState.value = tier
    }

    fun onProtocolSelected(protocol: ProtocolFilter) {
        selectedProtocolState.value = protocol
    }

    fun onSortSelected(sort: SortOption) {
        currentSortState.value = sort
    }

    fun toggleFavorite(id: String) {
        favoriteIdsState.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun testPingForServer(server: PublicServer) {
        viewModelScope.launch(dispatcher) {
            pingingIdsState.update { it + server.id }
            try {
                kotlinx.coroutines.delay(600)
            } finally {
                pingingIdsState.update { it - server.id }
            }
        }
    }

    fun testAllVisible() {
        val current = uiState.value.servers
        if (current.isEmpty()) return
        viewModelScope.launch(dispatcher) {
            val semaphore = Semaphore(5)
            current.forEach { server ->
                launch {
                    semaphore.withPermit {
                        pingingIdsState.update { it + server.id }
                        try {
                            kotlinx.coroutines.delay(400)
                        } finally {
                            pingingIdsState.update { it - server.id }
                        }
                    }
                }
            }
        }
    }
}
