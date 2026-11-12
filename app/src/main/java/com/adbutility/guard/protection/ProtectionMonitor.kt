package com.adbutility.guard.protection

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adbutility.guard.PreferencesManager
import com.adbutility.guard.ResetAccessibilityService
import com.adbutility.guard.ResetGuardManager
import com.adbutility.guard.admin.DeviceAdminManager
import java.util.concurrent.TimeUnit

/**
 * Monitor central de App Protection.
 * Evalúa el estado real de Device Admin, servicio de accesibilidad, notificaciones
 * y configuración básica, sincronizando avisos y comprobaciones periódicas con WorkManager.
 */
class ProtectionMonitor(
    private val appContext: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "ProtectionMonitor"
        private const val PERIODIC_WORK_NAME = "adbutility_app_protection_periodic_check"
        private const val CHECK_INTERVAL_HOURS = 6L
    }

    val repository = ProtectionRepository(appContext)
    val deviceAdminManager = DeviceAdminManager(appContext, repository)
    val warningManager = ProtectionWarningManager(appContext, repository)
    val recoveryManager = ProtectionRecoveryManager(appContext, deviceAdminManager)

    @Volatile
    private var lastSnapshot: ProtectionSnapshot? = null

    /**
     * Comprueba el estado real de todas las capas de protección sin confiar en flags estáticos.
     */
    @Synchronized
    fun evaluateProtection(
        notifyOnRegression: Boolean = true,
        forceTransitionNotice: Boolean = false
    ): ProtectionSnapshot {
        val adminActive = deviceAdminManager.reconcileWithRealState(deviceAdminManager.isAdminActive())
        val serviceActive = reconcileAccessibilityState(isAccessibilityServiceReallyActive())
        val notificationsAllowed = warningManager.areNotificationsAllowed()
        val basicConfigured = preferencesManager.isProtectionEnabled

        if (adminActive && serviceActive && basicConfigured) {
            repository.clearPendingNotices()
        }

        val snapshot = ProtectionState.derive(
            isDeviceAdminActive = adminActive,
            isAccessibilityServiceActive = serviceActive,
            areNotificationsEnabled = notificationsAllowed,
            isBasicProtectionConfigured = basicConfigured,
            isAdminDisabledNoticePending = repository.isAdminDisabledNoticePending,
            isServiceDisabledNoticePending = repository.isServiceDisabledNoticePending,
            evaluatedAtMs = System.currentTimeMillis()
        )

        repository.lastEvaluationAtMs = snapshot.evaluatedAtMs
        lastSnapshot = snapshot

        if (notifyOnRegression) {
            warningManager.notifyIfProtectionLost(snapshot, forceTransition = forceTransitionNotice)
        } else if (snapshot.level == ProtectionLevel.FULL_PROTECTION) {
            warningManager.cancelActiveWarning()
            repository.clearNotificationDeduplication()
        }

        return snapshot
    }

    fun getLastOrEvaluate(): ProtectionSnapshot {
        return evaluateProtection(notifyOnRegression = false)
    }

    fun handleDeviceAdminEnabled() {
        deviceAdminManager.onAdminActivated()
        evaluateProtection(notifyOnRegression = true)
    }

    fun handleDeviceAdminDisabled() {
        deviceAdminManager.onAdminDeactivated()
        evaluateProtection(notifyOnRegression = true, forceTransitionNotice = true)
    }

    fun handleAccessibilityServiceConnected() {
        repository.recordServiceEnabled()
        evaluateProtection(notifyOnRegression = true)
    }

    fun handleAccessibilityServiceDisabled() {
        repository.recordServiceDisabled()
        evaluateProtection(notifyOnRegression = true, forceTransitionNotice = true)
    }

    fun onBootOrPackageReplaced() {
        Log.i(TAG, "Evaluando protección tras arranque o actualización.")
        schedulePeriodicChecks()
        evaluateProtection(notifyOnRegression = true)
    }

    /**
     * Programa comprobaciones periódicas razonables mediante WorkManager sin consumir batería continuamente.
     * WorkManager no garantiza ejecución en tiempo real; complementa las comprobaciones en primer plano y receptores.
     */
    fun schedulePeriodicChecks() {
        try {
            val request = PeriodicWorkRequestBuilder<ProtectionCheckWorker>(
                CHECK_INTERVAL_HOURS,
                TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error programando comprobación periódica con WorkManager: ${e.message}")
        }
    }

    /**
     * Comprueba realmente en el sistema si ResetAccessibilityService está habilitado y activo.
     */
    fun isAccessibilityServiceReallyActive(): Boolean {
        val expectedCanonical =
            "${appContext.packageName}/${ResetAccessibilityService::class.java.canonicalName}"
        val expectedShort =
            "${appContext.packageName}/.${ResetAccessibilityService::class.java.simpleName}"

        // 1. Verificar lista real reportada por AccessibilityManager
        try {
            val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (info in enabledList) {
                    val id = info.id ?: continue
                    if (id.equals(expectedCanonical, ignoreCase = true) ||
                        id.equals(expectedShort, ignoreCase = true) ||
                        (id.startsWith(appContext.packageName) &&
                            id.contains(ResetAccessibilityService::class.java.simpleName))
                    ) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Respaldo mediante Settings.Secure
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = splitter.next()
            if (componentName.equals(expectedCanonical, ignoreCase = true) ||
                componentName.equals(expectedShort, ignoreCase = true) ||
                (componentName.startsWith(appContext.packageName) &&
                    componentName.contains(ResetAccessibilityService::class.java.simpleName))
            ) {
                return true
            }
        }
        return false
    }

    private fun reconcileAccessibilityState(activeNow: Boolean): Boolean {
        val lastKnown = repository.lastKnownServiceActive
        if (activeNow && !lastKnown) {
            repository.recordServiceEnabled()
        } else if (!activeNow && lastKnown) {
            repository.recordServiceDisabled()
        }
        return activeNow
    }
}

/**
 * Worker periódico de bajo impacto para verificar el estado real de protección.
 */
class ProtectionCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ResetGuardManager.getInstance(applicationContext)
                .protectionMonitor
                .evaluateProtection(notifyOnRegression = true)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

