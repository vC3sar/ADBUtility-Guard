package com.adbutility.guard.protection

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adbutility.guard.R
import com.adbutility.guard.ui.protection.AppProtectionActivity

/**
 * Gestor de avisos de protección.
 * Notifica de forma sobria, clara y deduplicada cuando alguna protección esencial
 * deja de estar activa, evitando notificaciones repetitivas o alarmistas.
 */
class ProtectionWarningManager(
    private val appContext: Context,
    private val repository: ProtectionRepository
) {

    companion object {
        private const val TAG = "ProtectionWarnMgr"
        private const val CHANNEL_ID = "app_protection_alerts"
        private const val NOTIFICATION_ID = 4101
        private const val MIN_REPEAT_COOLDOWN_MS = 6L * 60L * 60L * 1000L // 6 horas para el mismo estado
    }

    fun areNotificationsAllowed(): Boolean {
        val managerEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        if (!managerEnabled) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Evalúa si debe emitirse una notificación por pérdida de protección y la muestra
     * únicamente una vez por cada transición/estado degradado.
     */
    fun notifyIfProtectionLost(snapshot: ProtectionSnapshot, forceTransition: Boolean = false) {
        if (snapshot.level == ProtectionLevel.FULL_PROTECTION) {
            cancelActiveWarning()
            repository.clearNotificationDeduplication()
            return
        }

        // No spamear en primera instalación si el usuario nunca activó ninguna protección aún,
        // salvo que se trate de una transición explícita de pérdida o degradación previa.
        val hadPriorSetup = repository.wasAdminEverEnabled || repository.wasServiceEverEnabled
        if (!hadPriorSetup && !snapshot.hasPendingDeactivationNotice && !forceTransition) {
            return
        }

        val stateSignature = buildStateSignature(snapshot)
        val now = System.currentTimeMillis()
        val sameSignature = stateSignature == repository.lastNotifiedSignature
        val withinCooldown = (now - repository.lastNotifiedAtMs) < MIN_REPEAT_COOLDOWN_MS

        if (sameSignature && withinCooldown) {
            Log.d(TAG, "Aviso omitido por deduplicación para estado: $stateSignature")
            return
        }

        if (!areNotificationsAllowed()) {
            Log.d(TAG, "Notificaciones no permitidas actualmente; el aviso quedará visible dentro de la app.")
            return
        }

        showDeactivationNotification()
        repository.lastNotifiedSignature = stateSignature
        repository.lastNotifiedAtMs = now
    }

    fun cancelActiveWarning() {
        try {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun showDeactivationNotification() {
        ensureNotificationChannel()

        val reviewIntent = Intent(appContext, AppProtectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            reviewIntent,
            pendingIntentFlags
        )

        val title = appContext.getString(R.string.app_protection_warning_title)
        val message = appContext.getString(R.string.app_protection_warning_message)
        val actionText = appContext.getString(R.string.app_protection_warning_action)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guard_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(0, actionText, pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "Notificación de pérdida de protección emitida.")
        } catch (se: SecurityException) {
            Log.w(TAG, "Sin permiso de notificaciones para emitir alerta: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación de protección: ${e.message}")
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.app_protection_notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = appContext.getString(R.string.app_protection_notif_channel_desc)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildStateSignature(snapshot: ProtectionSnapshot): String {
        return "${snapshot.level.name}|adm=${snapshot.isDeviceAdminActive}|svc=${snapshot.isAccessibilityServiceActive}"
    }
}

