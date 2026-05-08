package com.sonuverma.deeploader.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkMonitor — Reactive network state observer.
 *
 * Provides real-time network connectivity info as StateFlow:
 *   - isConnected: Boolean (any internet)
 *   - isWifi: Boolean (on WiFi specifically)
 *   - isMetered: Boolean (mobile data / hotspot)
 *   - linkSpeedMbps: Int (WiFi link speed)
 *
 * Used by DownloadManager to enforce WiFi-only downloads and
 * by the UI to show connectivity indicators.
 *
 * Developer: Sonu Verma
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * Start monitoring network changes.
     * Call in Application.onCreate() or when needed.
     */
    fun startMonitoring() {
        if (callback != null) return // Already monitoring

        // Get initial state
        updateNetworkState()

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState()
            }

            override fun onLost(network: Network) {
                _networkState.value = NetworkState(isConnected = false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                val isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

                val downBandwidth = capabilities.linkDownstreamBandwidthKbps
                val upBandwidth = capabilities.linkUpstreamBandwidthKbps

                _networkState.value = NetworkState(
                    isConnected = true,
                    isWifi = isWifi,
                    isCellular = isCellular,
                    isEthernet = isEthernet,
                    isMetered = isMetered,
                    downBandwidthKbps = downBandwidth,
                    upBandwidthKbps = upBandwidth
                )
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Stop monitoring network changes.
     */
    fun stopMonitoring() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister callback", e)
            }
        }
        callback = null
    }

    /**
     * Get current network state snapshot (non-reactive).
     */
    private fun updateNetworkState() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        _networkState.value = if (capabilities != null) {
            NetworkState(
                isConnected = true,
                isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                downBandwidthKbps = capabilities.linkDownstreamBandwidthKbps,
                upBandwidthKbps = capabilities.linkUpstreamBandwidthKbps
            )
        } else {
            NetworkState(isConnected = false)
        }
    }
}

/**
 * Snapshot of current network state.
 */
data class NetworkState(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val isEthernet: Boolean = false,
    val isMetered: Boolean = false,
    val downBandwidthKbps: Int = 0,
    val upBandwidthKbps: Int = 0
) {
    val displayType: String
        get() = when {
            isWifi -> "WiFi"
            isCellular -> "Mobile"
            isEthernet -> "Ethernet"
            isConnected -> "Connected"
            else -> "Offline"
        }

    val displayBandwidth: String
        get() = when {
            downBandwidthKbps <= 0 -> ""
            downBandwidthKbps < 1000 -> "${downBandwidthKbps} Kbps"
            else -> "${"%.1f".format(downBandwidthKbps / 1000.0)} Mbps"
        }
}
