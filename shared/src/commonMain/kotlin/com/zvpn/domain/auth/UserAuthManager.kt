package com.zvpn.domain.auth

import com.zvpn.platform.KeyValueStore
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
    val isSignedIn: Boolean get() = email.isNotBlank() || userId.isNotBlank()
    val isProActive: Boolean get() = isPro && (proExpiresMs == 0L || proExpiresMs > System.currentTimeMillis())
}

object UserAuthManager {
    private const val KEY_USER_ID = "zvpn_user_id"
    private const val KEY_USER_EMAIL = "zvpn_user_email"
    private const val KEY_USER_NAME = "zvpn_user_name"
    private const val KEY_USER_TOKEN = "zvpn_user_token"

    private var store: KeyValueStore? = null

    private val _session = MutableStateFlow(UserSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        val uid = keyValueStore.getString(KEY_USER_ID) ?: ""
        val email = keyValueStore.getString(KEY_USER_EMAIL) ?: ""
        val name = keyValueStore.getString(KEY_USER_NAME) ?: ""
        val token = keyValueStore.getString(KEY_USER_TOKEN) ?: ""

        if (email.isNotBlank() || uid.isNotBlank()) {
            _session.value = UserSession(
                userId = uid,
                email = email,
                displayName = name,
                isAnonymous = false,
                token = token
            )
        }
    }

    fun updateSession(user: UserSession) {
        _session.value = user
        store?.putString(KEY_USER_ID, user.userId)
        store?.putString(KEY_USER_EMAIL, user.email)
        store?.putString(KEY_USER_NAME, user.displayName)
        store?.putString(KEY_USER_TOKEN, user.token)
    }

    fun setProStatus(isPro: Boolean, expiresMs: Long = 0L) {
        _session.value = _session.value.copy(isPro = isPro, proExpiresMs = expiresMs)
    }

    fun signOut() {
        _session.value = UserSession()
        store?.remove(KEY_USER_ID)
        store?.remove(KEY_USER_EMAIL)
        store?.remove(KEY_USER_NAME)
        store?.remove(KEY_USER_TOKEN)
    }
}
