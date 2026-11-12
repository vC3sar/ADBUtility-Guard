package com.adbutility.guard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adbutility.guard.ResetGuardManager

/**
 * Receptor de eventos de arranque del sistema y actualización del paquete.
 * Asegura la recuperación y persistencia del estado tras el reinicio del dispositivo.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == action
        ) {
            Log.i(TAG, "Evento recibido: $action. Verificando estado de ADBUtility Guard...")
            val guardManager = ResetGuardManager.getInstance(context)

            guardManager.protectionMonitor.onBootOrPackageReplaced()

            val serviceEnabled = guardManager.isAccessibilityServiceEnabled()
            val protectionActive = guardManager.preferencesManager.isProtectionEnabled

            Log.i(TAG, "Estado post-arranque: Servicio=$serviceEnabled, Protección=$protectionActive")
        }
    }
}

