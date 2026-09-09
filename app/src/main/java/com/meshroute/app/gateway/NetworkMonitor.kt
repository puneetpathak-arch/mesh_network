package com.meshroute.app.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkMonitor {
    val isInternetAvailable: StateFlow<Boolean>
    fun start()
    fun stop()
}

class AndroidNetworkMonitor(
    private val context: Context
) : NetworkMonitor {

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isInternetAvailable = MutableStateFlow(false)
    override val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun start() {
        if (connectivityManager == null) {
            Log.w(TAG, "ConnectivityManager unavailable on this device")
            return
        }

        _isInternetAvailable.value = checkCurrentConnectivity()
        Log.i(TAG, "NetworkMonitor started. Initial internet availability: ${_isInternetAvailable.value}")

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Internet connection acquired: $network")
                _isInternetAvailable.value = true
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Internet connection lost: $network")
                _isInternetAvailable.value = checkCurrentConnectivity()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                _isInternetAvailable.value = hasInternet
            }
        }
        networkCallback = cb

        runCatching {
            connectivityManager?.registerNetworkCallback(request, cb)
        }.onFailure {
            Log.e(TAG, "Failed to register network callback: ${it.message}")
        }
    }

    override fun stop() {
        networkCallback?.let {
            runCatching { connectivityManager?.unregisterNetworkCallback(it) }
        }
        networkCallback = null
        Log.i(TAG, "NetworkMonitor stopped")
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
