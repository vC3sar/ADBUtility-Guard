package com.adbutility.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.adbutility.guard.accessibility.AppProtectionAccessibilityMonitor
import com.adbutility.guard.protection.ProtectionMonitor
import com.adbutility.guard.updateguard.UpdateGuardEngine

/**
 * Orquestador principal de ADBUtility Guard.
 * Centraliza la comunicación entre detección, interfaz, persistencia y servicio de accesibilidad.
 */
class ResetGuardManager private constructor(context: Context) {

    val appContext: Context = context.applicationContext
    val preferencesManager = PreferencesManager(appContext)
    val eventLogger = EventLogger(appContext)
    val resetDetector = ResetDetector(preferencesManager)
    val overlayController = OverlayController(appContext, preferencesManager, eventLogger)
    val updateGuardEngine = UpdateGuardEngine(appContext, preferencesManager)
    val protectionMonitor = ProtectionMonitor(appContext, preferencesManager)
    val appProtectionMonitor = AppProtectionAccessibilityMonitor(protectionMonitor)

    var currentServiceInstance: AccessibilityService? = null

    companion object {
        @Volatile
        private var INSTANCE: ResetGuardManager? = null

        fun getInstance(context: Context): ResetGuardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResetGuardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Comprueba si el servicio de accesibilidad de la aplicación está actualmente habilitado en el sistema.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        if (currentServiceInstance != null) {
            return true
        }

        val expectedServiceName = "${appContext.packageName}/${ResetAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                componentName.contains(ResetAccessibilityService::class.java.simpleName)
            ) {
                return true
            }
        }
        return false
    }

    fun triggerTestOverlay(onAction: ((String) -> Unit)? = null) {
        overlayController.showWarning(
            service = currentServiceInstance,
            detectedPackage = "com.adbutility.guard.test",
            detectionType = "Prueba Manual / Test",
            onActionResolved = onAction
        )
    }
}

