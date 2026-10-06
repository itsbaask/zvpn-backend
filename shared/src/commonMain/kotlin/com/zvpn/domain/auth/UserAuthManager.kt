package com.zvpn.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val isAnonymous: Boolean = true,
    val isPro: Boolean = false,
    val proExpiresMs: Long = 0L,
    val token: String = "",
) {
    val isProActive: Boolean get() = isPro && (proExpiresMs == 0L || proExpiresMs > System.currentTimeMillis())
}

object UserAuthManager {
    private val _session = MutableStateFlow(UserSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

    fun updateSession(user: UserSession) {
        _session.value = user
    }

    fun setProStatus(isPro: Boolean, expiresMs: Long = 0L) {
        _session.value = _session.value.copy(isPro = isPro, proExpiresMs = expiresMs)
    }

    fun signOut() {
        _session.value = UserSession()
    }
}
