package com.cynosure.operit.integrations.lansync

import android.content.Context
import com.cynosure.operit.data.db.AppDatabase
import com.cynosure.operit.data.model.LanSyncPeerEntity
import com.cynosure.operit.integrations.http.ExternalChatHttpNetworkInfo
import fi.iki.elonen.NanoHTTPD
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LanSyncServer(context: Context, port: Int) : NanoHTTPD("0.0.0.0", port) {
    private val appContext = context.applicationContext
    private val preferences = LanSyncPreferences(appContext)
    private val dao = AppDatabase.getDatabase(appContext).lanSyncDao()
    private val engine = LanSyncEngine.getInstance(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun serve(session: IHTTPSession): Response = runBlocking {
        try {
            when {
                session.uri == "/lansync/v1/health" && session.method == Method.GET ->
                    response(Response.Status.OK, mapOf("deviceId" to preferences.deviceId, "deviceName" to preferences.deviceName))
                session.uri == "/lansync/v1/pair" && session.method == Method.POST -> pair(readBody(session))
                session.uri == "/lansync/v1/pull" && session.method == Method.POST -> authenticated(session) { peer ->
                    response(Response.Status.OK, engine.pull(peer.deviceId, json.decodeFromString<LanSyncPullRequest>(readBody(session))))
                }
                session.uri == "/lansync/v1/apply" && session.method == Method.POST -> authenticated(session) { peer ->
                    val result = engine.apply(peer.deviceId, json.decodeFromString<LanSyncEnvelope>(readBody(session)))
                    dao.markPeerSeen(peer.deviceId, System.currentTimeMillis())
                    response(Response.Status.OK, result)
                }
                session.uri == "/lansync/v1/ack" && session.method == Method.POST -> authenticated(session) { peer ->
                    val request = json.decodeFromString<LanSyncAckRequest>(readBody(session))
                    engine.acknowledge(peer.deviceId, "out", request.sequences)
                    response(Response.Status.OK, mapOf("accepted" to "true"))
                }
                else -> response(Response.Status.NOT_FOUND, mapOf("error" to "Endpoint not found"))
            }
        } catch (error: Exception) {
            response(Response.Status.BAD_REQUEST, mapOf("error" to (error.message ?: error.javaClass.simpleName)))
        }
    }

    private suspend fun pair(rawBody: String): Response {
        val request = json.decodeFromString<LanSyncPairRequest>(rawBody)
        require(request.pairingCode == preferences.pairingCode) { "Invalid pairing code" }
        require(request.deviceId != preferences.deviceId) { "Cannot pair with this device" }
        val tokenForServer = randomToken()
        dao.upsertPeer(
            LanSyncPeerEntity(
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                host = request.host,
                port = request.port,
                outgoingToken = request.tokenForRequester,
                incomingToken = tokenForServer,
                enabledCollections = request.enabledCollections.filter { it in LanSyncCollections.all }.joinToString(","),
                lastSeenAt = System.currentTimeMillis(),
            )
        )
        val nextPairingCode = preferences.rotatePairingCode()
        LanSyncState.update { it.copy(pairingCode = nextPairingCode) }
        val host = ExternalChatHttpNetworkInfo.getLocalIpv4Addresses().firstOrNull().orEmpty()
        return response(
            Response.Status.OK,
            LanSyncPairResponse(preferences.deviceId, preferences.deviceName, host, preferences.port, tokenForServer),
        )
    }

    private suspend fun authenticated(session: IHTTPSession, block: suspend (LanSyncPeerEntity) -> Response): Response {
        val authorization = session.headers.entries.firstOrNull { it.key.equals("authorization", true) }?.value.orEmpty()
        val token = authorization.removePrefix("Bearer ").trim()
        val peer = dao.getPeerByIncomingToken(token)
            ?: return response(Response.Status.UNAUTHORIZED, mapOf("error" to "Invalid peer token"))
        return block(peer)
    }

    private fun readBody(session: IHTTPSession): String {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        return files["postData"].orEmpty()
    }

    private inline fun <reified T> response(status: Response.Status, body: T): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", json.encodeToString(body))

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
