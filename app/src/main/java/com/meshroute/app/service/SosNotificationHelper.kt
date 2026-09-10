package com.meshroute.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meshroute.app.MainActivity
import com.meshroute.app.R
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SosNotificationHelper {

    private const val TAG = "SosNotificationHelper"
    const val SOS_CHANNEL_ID = "meshroute_emergency_sos_channel"
    private const val SOS_CHANNEL_NAME = "MeshRoute Emergency SOS Alerts"
    private const val SOS_CHANNEL_DESCRIPTION = "High-priority emergency alerts broadcasted across the BLE mesh"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                SOS_CHANNEL_ID,
                SOS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = SOS_CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Decrypts and shows a rich high-priority heads-up notification for an incoming SOS packet.
     */
    fun showSosNotification(context: Context, packet: SosPacket) {
        createNotificationChannel(context)

        // 1. Attempt decryption of emergency payload
        var emergencyPayload: EmergencyPayload? = null
        try {
            val decryptedJson = CryptoManager.decryptString(packet.payload, KeyManager.defaultEmergencyKey)
            emergencyPayload = EmergencyPayload.fromJson(decryptedJson)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt emergency payload with default key for ${packet.messageId}: ${e.message}")
        }

        val senderName = emergencyPayload?.senderName?.ifBlank { null } ?: "Node ${packet.originatorId}"
        val sosMessage = emergencyPayload?.message?.ifBlank { null } ?: "Emergency SOS alert received over mesh"
        val medicalInfo = emergencyPayload?.medicalInfo?.ifBlank { null } ?: "None specified"
        val batteryText = if ((emergencyPayload?.batteryPercent ?: -1) >= 0) "${emergencyPayload?.batteryPercent}%" else "Unknown"

        val loc = packet.location
        val locationText = if (loc != null) {
            val latStr = String.format(Locale.US, "%.5f", loc.latitude)
            val lonStr = String.format(Locale.US, "%.5f", loc.longitude)
            val accStr = if (loc.accuracy != null) " (±${loc.accuracy.toInt()}m)" else ""
            "📍 $latStr, $lonStr$accStr"
        } else {
            "📍 Location unavailable"
        }

        val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(packet.timestamp))

        // 2. Main Tap Intent -> Opens MainActivity
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SOS_PACKET_ID", packet.messageId)
            putExtra("EXTRA_ORIGINATOR_ID", packet.originatorId)
            putExtra("EXTRA_SENDER_NAME", senderName)
            putExtra("EXTRA_MESSAGE", sosMessage)
            if (loc != null) {
                putExtra("EXTRA_LATITUDE", loc.latitude)
                putExtra("EXTRA_LONGITUDE", loc.longitude)
            }
        }

        val pendingAppIntent = PendingIntent.getActivity(
            context,
            packet.messageId.hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Expanded BigText style
        val bigText = buildString {
            append("🚨 EMERGENCY SOS BROADCAST\n")
            append("👤 From: $senderName (${packet.originatorId})\n")
            append("📝 Message: $sosMessage\n")
            append("$locationText\n")
            append("🩺 Medical Notes: $medicalInfo\n")
            append("🔋 Battery: $batteryText | Hops away: ${packet.hops}\n")
            append("⏰ Time: $formattedTime")
        }

        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 SOS ALERT: $senderName")
            .setContentText("$sosMessage • $locationText")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingAppIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))

        // 4. Action: "View on Map" if coordinates exist
        if (loc != null) {
            val mapUri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(SOS+Alert+-+${Uri.encode(senderName)})")
            val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingMapIntent = PendingIntent.getActivity(
                context,
                packet.messageId.hashCode() + 1,
                mapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "📍 View on Map", pendingMapIntent)
        }

        // Action: "Open App"
        builder.addAction(0, "🚨 Open MeshRoute", pendingAppIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // Use positive hash code for unique notification IDs
            val notificationId = (packet.messageId.hashCode() and 0x7FFFFFFF)
            notificationManager.notify(notificationId, builder.build())
            Log.i(TAG, "Dispatched rich emergency SOS notification for ${packet.messageId} (Sender: $senderName, Loc: $locationText)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing when dispatching SOS notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch SOS notification: ${e.message}", e)
        }
    }
}
