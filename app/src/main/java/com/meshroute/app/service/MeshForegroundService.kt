package com.meshroute.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshroute.app.MainActivity
import com.meshroute.app.R
import com.meshroute.app.gateway.AndroidNetworkMonitor
import com.meshroute.app.gateway.GatewayUploader
import com.meshroute.app.gateway.NetworkMonitor
import com.meshroute.app.mesh.ble.BleMeshTransport
import com.meshroute.app.mesh.router.MeshRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dedicated Foreground Service for MeshRoute background radio operations:
 * Runs continuous BLE discovery, Mesh Router packet handling, Nostr Internet bridging,
 * and adaptive power management under Android 12+/14+ background execution rules.
 */
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "meshroute_service_channel"

        const val ACTION_START = "com.meshroute.app.action.START_MESH"
        const val ACTION_STOP = "com.meshroute.app.action.STOP_MESH"

        fun startService(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MeshForegroundService = this@MeshForegroundService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var bleTransport: BleMeshTransport? = null
        private set
    var router: MeshRouter? = null
        private set
    var gatewayUploader: GatewayUploader? = null
        private set

    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "MeshForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received STOP command for MeshForegroundService")
                stopMeshOperations()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                if (!isRunning) {
                    startMeshOperations()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("MeshRoute Safety Mesh Active", "Scanning & relaying off-grid packets")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMeshOperations() {
        if (isRunning) return
        isRunning = true

        val nodeId = "Node_${Build.MODEL.replace(" ", "_")}_${(1000..9999).random()}"
        val transport = BleMeshTransport(applicationContext, nodeId)
        val netMonitor = AndroidNetworkMonitor(applicationContext)

        this.bleTransport = transport

        scope.launch {
            transport.start()
            Log.i(TAG, "BLE Transport started under FGS for node: $nodeId")
        }
    }

    private fun stopMeshOperations() {
        if (!isRunning) return
        isRunning = false

        scope.launch {
            bleTransport?.stop()
            gatewayUploader?.stop()
            Log.i(TAG, "Mesh operations stopped under FGS")
        }
    }

    override fun onDestroy() {
        stopMeshOperations()
        scope.cancel()
        super.onDestroy()
        Log.i(TAG, "MeshForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MeshRoute Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BLE mesh and emergency transport active in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
