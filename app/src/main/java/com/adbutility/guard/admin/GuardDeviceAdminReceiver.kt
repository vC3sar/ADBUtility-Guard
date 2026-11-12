package com.adbutility.guard.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adbutility.guard.R
import com.adbutility.guard.ResetGuardManager

/**
 * Receptor oficial de eventos de administración del dispositivo (Device Admin).
 * Detecta la activación y desactivación voluntaria sin interferir ni bloquear al propietario.
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "GuardDeviceAdminRx"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin activado por el usuario.")
        ResetGuardManager.getInstance(context).protectionMonitor.handleDeviceAdminEnabled()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.app_protection_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin desactivado. Actualizando estado de protección.")
        ResetGuardManager.getInstance(context).protectionMonitor.handleDeviceAdminDisabled()
    }
}

