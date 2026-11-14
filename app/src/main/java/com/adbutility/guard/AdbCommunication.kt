package com.adbutility.guard

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject

/**
 * Módulo de comunicación y respuesta para la integración de ADB UtilityTools (escritorio).
 * Proporciona métodos de consulta estructurados en formato JSON.
 * REGLA ESTRICTA: Solo permite operaciones de lectura y consulta de estado;
 * bajo ninguna circunstancia ejecuta comandos arbitrarios del sistema.
 */
class AdbCommunication(private val context: Context) {

    private val guardManager = ResetGuardManager.getInstance(context)

    fun getAppVersionInfo(): JSONObject {
        var versionName = "1.0.0"
        var versionCode = 100L

        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            versionName = pInfo.versionName ?: "1.0.0"
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
        }

        return JSONObject().apply {
            put("packageName", context.packageName)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("isInstalled", true)
        }
    }

    fun getServiceStatus(): JSONObject {
        val prefs = guardManager.preferencesManager
        val isServiceEnabled = guardManager.isAccessibilityServiceEnabled()
        val isProtectionActive = prefs.isProtectionEnabled
        val cooldown = prefs.cooldownSeconds
        val totalDetections = prefs.totalDetectionsCount

        val updateGuardEnabled = guardManager.updateGuardEngine.settings.isUpdateGuardEnabled
        val updateBlockedCount = guardManager.updateGuardEngine.eventLogger.getBlockedCount()

        val protectionSnapshot = guardManager.protectionMonitor.evaluateProtection(notifyOnRegression = false)

        return JSONObject().apply {
            put("serviceEnabled", isServiceEnabled)
            put("protectionEnabled", isProtectionActive)
            put("effectiveProtectionActive", prefs.isEffectiveProtectionActive())
            put("cooldownSeconds", cooldown)
            put("totalDetections", totalDetections)
            put(
                "technicianMode",
                JSONObject().apply {
                    put("pinEnabled", prefs.hasTechnicianPin())
                    put("paused", prefs.isTechnicianModePaused())
                    put("pauseRemainingSeconds", prefs.getTechnicianPauseRemainingSeconds())
                }
            )
            put(
                "updateGuard",
                JSONObject().apply {
                    put("enabled", updateGuardEnabled)
                    put("active", updateGuardEnabled && isServiceEnabled && !prefs.isTechnicianModePaused())
                    put("blockedCount", updateBlockedCount)
                }
            )
            put(
                "appProtection",
                JSONObject().apply {
                    put("level", protectionSnapshot.level.name)
                    put("deviceAdminActive", protectionSnapshot.isDeviceAdminActive)
                    put("accessibilityServiceActive", protectionSnapshot.isAccessibilityServiceActive)
                    put("notificationsEnabled", protectionSnapshot.areNotificationsEnabled)
                    put("supervisionActive", protectionSnapshot.isSecuritySupervisionActive)
                    put("pendingDeactivationNotice", protectionSnapshot.hasPendingDeactivationNotice)
                }
            )
            put("deviceInfo", DeviceInfo.toJson())
        }
    }

    fun getUpdateGuardDiagnostics(limit: Int = 30): JSONObject {
        return guardManager.updateGuardEngine.eventLogger.getDiagnosticsAsJson(limit)
    }

    fun getLastDetection(): JSONObject {
        val lastEvent = guardManager.eventLogger.getLatestEvent()
        return if (lastEvent != null) {
            JSONObject().apply {
                put("hasDetections", true)
                put("event", lastEvent.toJson())
            }
        } else {
            val pkg = guardManager.preferencesManager.lastDetectionPackage
            val type = guardManager.preferencesManager.lastDetectionType
            val time = guardManager.preferencesManager.lastDetectionTime
            val action = guardManager.preferencesManager.lastActionTaken

            JSONObject().apply {
                put("hasDetections", time > 0)
                put("timestamp", time)
                put("packageName", pkg)
                put("detectionType", type)
                put("actionTaken", action)
            }
        }
    }

    fun getDetectionHistory(limit: Int = 50): JSONObject {
        return guardManager.eventLogger.getHistoryAsJson(limit)
    }

    fun clearHistory(): JSONObject {
        guardManager.eventLogger.clearHistory()
        return JSONObject().apply {
            put("success", true)
            put("message", "History cleared successfully")
        }
    }
}

