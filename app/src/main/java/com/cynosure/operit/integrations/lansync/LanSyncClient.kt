package com.cynosure.operit.integrations.lansync

import android.content.Context
import com.cynosure.operit.data.db.AppDatabase
import com.cynosure.operit.data.model.LanSyncPeerEntity
import com.cynosure.operit.integrations.http.ExternalChatHttpNetworkInfo
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LanSyncClient(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = LanSyncPreferences(appContext)
    private val dao = AppDatabase.getDatabase(appContext).lanSyncDao()
    private val engine = LanSyncEngine.getInstance(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun pair(host: String, port: Int, pairingCode: String, collections: Set<String>): LanSyncPeerEntity = withContext(Dispatchers.IO) {
        val incomingToken = randomToken()
        val localHost = ExternalChatHttpNetworkInfo.getLocalIpv4Addresses().firstOrNull().orEmpty()
        val response = post<LanSyncPairRequest, LanSyncPairResponse>(
            "http://${host.trim()}:$port/lansync/v1/pair",
            LanSyncPairRequest(
                pairingCode = pairingCode.trim(),
                deviceId = preferences.deviceId,
                deviceName = preferences.deviceName,
                host = localHost,
                port = preferences.port,
                tokenForRequester = incomingToken,
                enabledCollections = collections.toList(),
            ),
            null,
        )
        LanSyncPeerEntity(
            deviceId = response.deviceId,
            deviceName = response.deviceName,
            host = host.trim(),
            port = response.port,
            outgoingToken = response.tokenForServer,
            incomingToken = incomingToken,
            enabledCollections = collections.joinToString(","),
            lastSeenAt = System.currentTimeMillis(),
        ).also { dao.upsertPeer(it) }
    }

    suspend fun sync(peerDeviceId: String): LanSyncApplyResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val peer = requireNotNull(dao.getPeer(peerDeviceId)) { "Peer not found" }
            val enabled = peer.enabledCollections.split(',').filter { it in LanSyncCollections.all }.toSet()
            LanSyncState.update { it.copy(state = LanSyncSessionState.SYNCING, activePeerDeviceId = peerDeviceId, lastError = null) }
            try {
                engine.scan(enabled)
                val inboundCursors = enabled.associateWith { 0L } + engine.cursors(peerDeviceId, "in")
                val pulled = post<LanSyncPullRequest, LanSyncPullResponse>(url(peer, "pull"), LanSyncPullRequest(inboundCursors), peer.outgoingToken)
                val inboundResult = engine.apply(peerDeviceId, pulled.envelope)
                engine.acknowledge(peerDeviceId, "in", inboundResult.acceptedSequences)
                post<LanSyncAckRequest, Map<String, String>>(url(peer, "ack"), LanSyncAckRequest(inboundResult.acceptedSequences), peer.outgoingToken)

                val outboundCursors = enabled.associateWith { 0L } + engine.cursors(peerDeviceId, "out")
                val outbound = engine.pull(peerDeviceId, LanSyncPullRequest(outboundCursors))
                val outboundResult = post<LanSyncEnvelope, LanSyncApplyResult>(url(peer, "apply"), outbound.envelope, peer.outgoingToken)
                engine.acknowledge(peerDeviceId, "out", outboundResult.acceptedSequences)
                val conflicts = inboundResult.conflicts + outboundResult.conflicts
                val result = LanSyncApplyResult(
                    batchId = outboundResult.batchId,
                    acceptedEntityIds = inboundResult.acceptedEntityIds + outboundResult.acceptedEntityIds,
                    conflicts = conflicts,
                    acceptedSequences = inboundResult.acceptedSequences + outboundResult.acceptedSequences,
                )
                dao.updatePeerSyncResult(peerDeviceId, System.currentTimeMillis(), null)
                LanSyncState.update {
                    it.copy(
                        state = LanSyncSessionState.COMPLETED,
                        sentChangeCount = outbound.envelope.changes.size,
                        receivedChangeCount = pulled.envelope.changes.size,
                        conflictCount = conflicts.size,
                    )
                }
                result
            } catch (error: Exception) {
                dao.updatePeerSyncResult(peerDeviceId, System.currentTimeMillis(), error.message)
                LanSyncState.update { it.copy(state = LanSyncSessionState.FAILED, lastError = error.message) }
                throw error
            }
        }
    }

    private fun url(peer: LanSyncPeerEntity, action: String) = "http://${peer.host}:${peer.port}/lansync/v1/$action"

    private inline fun <reified RequestType, reified ResponseType> post(
        url: String,
        body: RequestType,
        token: String?,
    ): ResponseType {
        val builder = Request.Builder().url(url)
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
        if (token != null) builder.header("Authorization", "Bearer $token")
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code}: $raw" }
            return json.decodeFromString(raw)
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val syncMutex = Mutex()
    }
}
