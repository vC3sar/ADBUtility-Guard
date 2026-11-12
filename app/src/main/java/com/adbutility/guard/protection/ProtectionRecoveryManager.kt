package com.adbutility.guard.protection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.adbutility.guard.admin.DeviceAdminManager

/**
 * Gestor de recuperación manual guiada.
 * Permite al propietario restaurar voluntariamente Device Admin, accesibilidad y notificaciones
 * mediante las pantallas oficiales de Android.
 */
class ProtectionRecoveryManager(
    private val appContext: Context,
    private val deviceAdminManager: DeviceAdminManager
) {

    fun createDeviceAdminActivationIntent(): Intent {
        return deviceAdminManager.createActivationIntent()
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun createNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", appContext.packageName, null)
            }
        }
    }

    fun openDeviceAdminActivation(activity: Activity, launcher: ((Intent) -> Unit)? = null) {
        val intent = createDeviceAdminActivationIntent()
        if (launcher != null) {
            launcher(intent)
        } else {
            activity.startActivity(intent)
        }
    }

    fun openAccessibilitySettings(activity: Activity) {
        activity.startActivity(createAccessibilitySettingsIntent())
    }

    fun openNotificationSettings(activity: Activity) {
        activity.startActivity(createNotificationSettingsIntent())
    }
}

