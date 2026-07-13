package com.cynosure.operit.integrations.lansync

import android.content.Context
import com.cynosure.operit.data.db.AppDatabase
import kotlinx.coroutines.flow.combine

class LanSyncManager private constructor(private val context: Context) {
    private val preferences = LanSyncPreferences(context)
    private val dao = AppDatabase.getDatabase(context).lanSyncDao()
    val peers = dao.observePeers()
    val conflicts = dao.observeOpenConflicts()
    val overview = combine(peers, conflicts, LanSyncState.snapshot) { peers, conflicts, snapshot ->
        LanSyncOverview(peers, conflicts.size, snapshot)
    }
    private var server: LanSyncServer? = null

    fun startServer(port: Int = preferences.port) {
        stopServer()
        preferences.port = port
        server = LanSyncServer(context, port).also { it.start(8_000, false) }
        LanSyncState.update {
            it.copy(serverRunning = true, serverPort = port, pairingCode = preferences.pairingCode, lastError = null)
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
        LanSyncState.update { it.copy(serverRunning = false) }
    }

    fun rotatePairingCode() {
        LanSyncState.update { it.copy(pairingCode = preferences.rotatePairingCode()) }
    }

    fun client(): LanSyncClient = LanSyncClient(context)

    suspend fun resolveConflict(conflictId: Long, resolution: LanSyncConflictResolution): Map<String, Long> =
        LanSyncEngine.getInstance(context).resolveConflict(conflictId, resolution)

    companion object {
        @Volatile private var instance: LanSyncManager? = null
        fun getInstance(context: Context): LanSyncManager = instance ?: synchronized(this) {
            instance ?: LanSyncManager(context.applicationContext).also { instance = it }
        }
    }
}

data class LanSyncOverview(
    val peers: List<com.cynosure.operit.data.model.LanSyncPeerEntity>,
    val conflictCount: Int,
    val snapshot: LanSyncSnapshot,
)
