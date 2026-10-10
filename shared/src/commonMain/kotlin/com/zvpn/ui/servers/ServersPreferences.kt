package com.zvpn.ui.servers

interface ServersPreferences {
    fun getFavorites(): Set<String>
    fun saveFavorites(ids: Set<String>)
    fun getLastTier(): String
    fun saveLastTier(tierId: String)
    fun getLastProtocol(): String
    fun saveLastProtocol(protocolId: String)
    fun getLastSort(): String
    fun saveLastSort(sortId: String)
}

class InMemoryServersPreferences : ServersPreferences {
    private var favorites = emptySet<String>()
    private var tier = "free"
    private var protocol = "all"
    private var sort = "recommended"

    override fun getFavorites(): Set<String> = favorites
    override fun saveFavorites(ids: Set<String>) { favorites = ids }
    override fun getLastTier(): String = tier
    override fun saveLastTier(tierId: String) { tier = tierId }
    override fun getLastProtocol(): String = protocol
    override fun saveLastProtocol(protocolId: String) { protocol = protocolId }
    override fun getLastSort(): String = sort
    override fun saveLastSort(sortId: String) { sort = sortId }
}
