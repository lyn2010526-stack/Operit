package com.cynosure.operit.integrations.lansync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LanSyncSnapshot(
    val state: LanSyncSessionState = LanSyncSessionState.IDLE,
    val discoveredDevices: List<LanSyncDevice> = emptyList(),
    val activePeerDeviceId: String? = null,
    val sentChangeCount: Int = 0,
    val receivedChangeCount: Int = 0,
    val lastError: String? = null
)

object LanSyncState {
    private val mutableSnapshot = MutableStateFlow(LanSyncSnapshot())
    val snapshot: StateFlow<LanSyncSnapshot> = mutableSnapshot.asStateFlow()

    fun update(transform: (LanSyncSnapshot) -> LanSyncSnapshot) {
        mutableSnapshot.value = transform(mutableSnapshot.value)
    }
}
