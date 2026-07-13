package com.cynosure.operit.services.core

import com.cynosure.operit.data.model.UserInputRequest
import com.cynosure.operit.data.model.UserInputResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

object UserInputRequestRepository {

    private data class RequestState(
        val request: UserInputRequest,
        val deferred: CompletableDeferred<UserInputResponse> = CompletableDeferred()
    )

    private val requests = ConcurrentHashMap<String, RequestState>()
    private val pendingRequestIds = ArrayDeque<String>()
    private val lock = Any()
    private val _pendingRequestFlow = MutableStateFlow<UserInputRequest?>(null)
    val pendingRequestFlow: StateFlow<UserInputRequest?> = _pendingRequestFlow.asStateFlow()

    fun createRequest(request: UserInputRequest): String {
        synchronized(lock) {
            check(requests.putIfAbsent(request.id, RequestState(request)) == null) {
                "User input request already exists: ${request.id}"
            }
            pendingRequestIds.addLast(request.id)
            if (_pendingRequestFlow.value == null) {
                _pendingRequestFlow.value = request
            }
        }
        return request.id
    }

    suspend fun awaitResponse(requestId: String): UserInputResponse {
        val state = synchronized(lock) {
            requests[requestId]
                ?: throw IllegalStateException("User input request not found: $requestId")
        }
        return state.deferred.await()
    }

    fun respond(requestId: String, answers: Map<String, String>): Boolean {
        synchronized(lock) {
            val state = requests[requestId] ?: return false
            if (!state.deferred.complete(UserInputResponse(requestId, answers))) return false
            removeRequestLocked(requestId)
            return true
        }
    }

    fun cancelRequest(requestId: String) {
        synchronized(lock) {
            val state = requests[requestId] ?: return
            state.deferred.completeExceptionally(IllegalStateException("User input request cancelled"))
            removeRequestLocked(requestId)
        }
    }

    private fun removeRequestLocked(requestId: String) {
        requests.remove(requestId)
        pendingRequestIds.remove(requestId)
        _pendingRequestFlow.value = pendingRequestIds.firstOrNull()?.let { requests[it]?.request }
    }

    fun getRequest(requestId: String): UserInputRequest? = requests[requestId]?.request
}
