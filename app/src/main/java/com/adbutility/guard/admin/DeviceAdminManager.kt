package com.adbutility.guard.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.adbutility.guard.R
import com.adbutility.guard.protection.ProtectionRepository

/**
 * Gestor de administración básica del dispositivo mediante DevicePolicyManager.
 * No utiliza Device Owner ni intenta reactivación silenciosa.
 */
class DeviceAdminManager(
    private val appContext: Context,
    private val repository: ProtectionRepository
) {

    val adminComponent: ComponentName =
        ComponentName(appContext, GuardDeviceAdminReceiver::class.java)

    private val devicePolicyManager: DevicePolicyManager?
        get() = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    /**
     * Consulta real del sistema sobre si la administración del dispositivo está activa.
     */
    fun isAdminActive(): Boolean {
        return try {
            devicePolicyManager?.isAdminActive(adminComponent) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Genera el Intent oficial del sistema para solicitar activación voluntaria de Device Admin.
     */
    fun createActivationIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                appContext.getString(R.string.app_protection_device_admin_explanation)
            )
        }
    }

    fun getStatus(): DeviceAdminStatus {
        val activeNow = isAdminActive()
        reconcileWithRealState(activeNow)

        return DeviceAdminStatus(
            isActive = activeNow,
            wasPreviouslyEnabled = repository.wasAdminEverEnabled,
            wasDisabledNoticePending = repository.isAdminDisabledNoticePending,
            lastEnabledAtMs = repository.lastAdminEnabledAtMs,
            lastDisabledAtMs = repository.lastAdminDisabledAtMs
        )
    }

    /**
     * Sincroniza el estado almacenado con la fuente real del sistema por si cambió
     * mientras la aplicación estaba detenida o tras reiniciar.
     */
    fun reconcileWithRealState(activeNow: Boolean = isAdminActive()): Boolean {
        val lastKnownActive = repository.lastKnownAdminActive
        if (activeNow && !lastKnownActive) {
            onAdminActivated()
        } else if (!activeNow && lastKnownActive) {
            onAdminDeactivated()
        }
        return activeNow
    }

    fun onAdminActivated() {
        repository.recordAdminEnabled()
    }

    fun onAdminDeactivated() {
        repository.recordAdminDisabled()
    }
}

