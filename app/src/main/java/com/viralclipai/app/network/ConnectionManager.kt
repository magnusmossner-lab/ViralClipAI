package com.viralclipai.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.viralclipai.app.data.api.ApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow

/**
 * ConnectionManager – Intelligente Verbindungs-KI für ViralClipAI
 *
 * Features:
 * - Automatische Server-Health-Checks mit adaptivem Intervall
 * - Exponential Backoff Retry bei Fehlern
 * - Automatische Reconnection bei Verbindungsverlust
 * - Netzwerk-Monitoring (WiFi/Mobile/Offline)
 * - Verbindungsqualität-Bewertung
 * - Intelligentes User-Feedback
 */
object ConnectionManager {

    private const val TAG = "ConnectionManager"

    // ─── Connection States ───
    enum class ConnectionState {
        CONNECTED,          // Server erreichbar & healthy
        DEGRADED,           // Server erreichbar aber nicht optimal
        RECONNECTING,       // Versuche Verbindung wiederherzustellen
        DISCONNECTED,       // Kein Server erreichbar
        NO_INTERNET         // Kein Internet verfügbar
    }

    enum class NetworkType {
        WIFI, MOBILE, NONE
    }

    data class ConnectionInfo(
        val state: ConnectionState = ConnectionState.DISCONNECTED,
        val networkType: NetworkType = NetworkType.NONE,
        val serverVersion: String = "",
        val aiModelsLoaded: Boolean = false,
        val latencyMs: Long = -1,
        val retryCount: Int = 0,
        val lastError: String? = null,
        val statusMessage: String = "Nicht verbunden"
    )

    // ─── State ───
    private val _connectionInfo = MutableStateFlow(ConnectionInfo())
    val connectionInfo: StateFlow<ConnectionInfo> = _connectionInfo

    val isConnected: Boolean
        get() = _connectionInfo.value.state == ConnectionState.CONNECTED ||
                _connectionInfo.value.state == ConnectionState.DEGRADED

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthCheckJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val consecutiveFailures = AtomicInteger(0)
    private val maxRetries = 10
    private val baseDelayMs = 2000L
    private val maxDelayMs = 60000L

    // ─── Init ───
    fun init(context: Context) {
        registerNetworkCallback(context)
        startHealthMonitoring()
        Log.i(TAG, "ConnectionManager initialisiert")
    }

    // ─── Network Monitoring ───
    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initial state
        updateNetworkType(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Netzwerk verfügbar")
                updateNetworkType(cm)
                // Sofort Server-Check wenn Netzwerk zurück
                if (_connectionInfo.value.state == ConnectionState.NO_INTERNET ||
                    _connectionInfo.value.state == ConnectionState.DISCONNECTED) {
                    scope.launch {
                        update(statusMessage = "Netzwerk erkannt – verbinde...")
                        checkServerHealth()
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Netzwerk verloren")
                update(
                    state = ConnectionState.NO_INTERNET,
                    networkType = NetworkType.NONE,
                    statusMessage = "Kein Internet – warte auf Verbindung..."
                )
                consecutiveFailures.set(0)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateNetworkType(cm)
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "NetworkCallback Fehler: ${e.message}")
        }
    }

    private fun updateNetworkType(cm: ConnectivityManager) {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val type = when {
            caps == null -> NetworkType.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else -> NetworkType.NONE
        }
        if (type == NetworkType.NONE) {
            update(networkType = type, state = ConnectionState.NO_INTERNET, statusMessage = "Kein Internet")
        } else {
            update(networkType = type)
        }
    }

    // ─── Health Monitoring ───
    private fun startHealthMonitoring() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                if (_connectionInfo.value.networkType != NetworkType.NONE) {
                    checkServerHealth()
                }
                // Adaptives Intervall: schneller bei Problemen, langsamer wenn stabil
                val interval = if (isConnected) 30_000L else getBackoffDelay()
                delay(interval)
            }
        }
    }

    private suspend fun checkServerHealth() {
        try {
            val startTime = System.currentTimeMillis()
            val response = ApiClient.getService().healthCheck()
            val latency = System.currentTimeMillis() - startTime

            consecutiveFailures.set(0)

            val state = when {
                response.status == "ok" -> ConnectionState.CONNECTED
                response.status == "degraded" -> ConnectionState.DEGRADED
                else -> ConnectionState.CONNECTED
            }

            update(
                state = state,
                serverVersion = response.version,
                aiModelsLoaded = response.aiModelsLoaded,
                latencyMs = latency,
                retryCount = 0,
                lastError = null,
                statusMessage = when (state) {
                    ConnectionState.CONNECTED -> "Server verbunden (${latency}ms)"
                    ConnectionState.DEGRADED -> "Server eingeschränkt – Verarbeitung möglich"
                    else -> "Verbunden"
                }
            )
            Log.i(TAG, "Health OK: $state, Latenz: ${latency}ms")
        } catch (e: Exception) {
            val failures = consecutiveFailures.incrementAndGet()
            Log.w(TAG, "Health Check fehlgeschlagen ($failures/$maxRetries): ${e.message}")

            if (failures >= maxRetries) {
                update(
                    state = ConnectionState.DISCONNECTED,
                    latencyMs = -1,
                    retryCount = failures,
                    lastError = e.message,
                    statusMessage = "Server nicht erreichbar – prüfe deine Verbindung"
                )
            } else {
                update(
                    state = ConnectionState.RECONNECTING,
                    latencyMs = -1,
                    retryCount = failures,
                    lastError = e.message,
                    statusMessage = "Verbindung wird wiederhergestellt... (Versuch $failures)"
                )
            }
        }
    }

    // ─── Retry Logic ───
    private fun getBackoffDelay(): Long {
        val failures = consecutiveFailures.get()
        val delayMs = baseDelayMs * 2.0.pow(failures.coerceAtMost(8).toDouble()).toLong()
        return min(delayMs, maxDelayMs)
    }

    /**
     * Führt einen API-Call mit intelligentem Retry aus.
     * Exponential Backoff + automatische Reconnection.
     */
    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        operation: String = "API-Call",
        block: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            // Warte auf Internet
            if (_connectionInfo.value.networkType == NetworkType.NONE) {
                return Result.failure(Exception("Kein Internet verfügbar"))
            }

            try {
                val result = block()
                // Erfolg → Reset Failures
                if (consecutiveFailures.get() > 0) {
                    consecutiveFailures.set(0)
                    checkServerHealth() // Re-check nach Recovery
                }
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "$operation fehlgeschlagen (Versuch $attempt/$maxAttempts): ${e.message}")

                if (attempt < maxAttempts) {
                    val retryDelayMs = baseDelayMs * 2.0.pow((attempt - 1).toDouble()).toLong()
                    val jitter = (0..500).random().toLong()
                    delay(min(retryDelayMs + jitter, maxDelayMs))
                }
            }
        }

        return Result.failure(lastException ?: Exception("$operation fehlgeschlagen nach $maxAttempts Versuchen"))
    }

    // ─── Manual Reconnect ───
    fun reconnect() {
        consecutiveFailures.set(0)
        update(state = ConnectionState.RECONNECTING, statusMessage = "Manuelle Verbindung...")
        scope.launch { checkServerHealth() }
    }

    fun onServerUrlChanged() {
        consecutiveFailures.set(0)
        update(state = ConnectionState.RECONNECTING, statusMessage = "Neue Server-URL – verbinde...")
        scope.launch { checkServerHealth() }
    }

    // ─── State Update Helper ───
    private fun update(
        state: ConnectionState? = null,
        networkType: NetworkType? = null,
        serverVersion: String? = null,
        aiModelsLoaded: Boolean? = null,
        latencyMs: Long? = null,
        retryCount: Int? = null,
        lastError: String? = null,
        statusMessage: String? = null
    ) {
        val current = _connectionInfo.value
        _connectionInfo.value = current.copy(
            state = state ?: current.state,
            networkType = networkType ?: current.networkType,
            serverVersion = serverVersion ?: current.serverVersion,
            aiModelsLoaded = aiModelsLoaded ?: current.aiModelsLoaded,
            latencyMs = latencyMs ?: current.latencyMs,
            retryCount = retryCount ?: current.retryCount,
            lastError = lastError,
            statusMessage = statusMessage ?: current.statusMessage
        )
    }

    // ─── Cleanup ───
    fun destroy(context: Context) {
        healthCheckJob?.cancel()
        networkCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        scope.cancel()
    }
}
