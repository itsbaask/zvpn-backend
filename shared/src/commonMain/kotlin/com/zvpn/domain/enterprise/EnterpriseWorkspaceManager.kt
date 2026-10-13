package com.zvpn.domain.enterprise

import com.zvpn.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkspaceOrg(
    val orgId: String = "",
    val orgName: String = "",
    val activeSeats: Int = 1,
    val dedicatedIp: String = "",
)

object EnterpriseWorkspaceManager {

    private const val KEY_ORG_ID = "zvpn_org_id"
    private const val KEY_ORG_NAME = "zvpn_org_name"

    private var store: KeyValueStore? = null

    private val _workspace = MutableStateFlow(WorkspaceOrg())
    val workspace: StateFlow<WorkspaceOrg> = _workspace.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        val id = keyValueStore.getString(KEY_ORG_ID) ?: ""
        val name = keyValueStore.getString(KEY_ORG_NAME) ?: ""

        if (id.isNotBlank()) {
            _workspace.value = WorkspaceOrg(orgId = id, orgName = name)
        }
    }

    fun joinWorkspace(orgId: String, orgName: String) {
        _workspace.value = WorkspaceOrg(orgId = orgId, orgName = orgName)
        store?.putString(KEY_ORG_ID, orgId)
        store?.putString(KEY_ORG_NAME, orgName)
    }

    fun leaveWorkspace() {
        _workspace.value = WorkspaceOrg()
        store?.remove(KEY_ORG_ID)
        store?.remove(KEY_ORG_NAME)
    }
}
