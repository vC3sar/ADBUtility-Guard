package com.adbutility.guard.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adbutility.guard.protection.ProtectionMonitor
import com.adbutility.guard.updateguard.UpdateTextDetector

/**
 * Monitor pasivo e independiente para eventos relacionados con la seguridad de la app.
 * Reutiliza el AccessibilityService existente sin bloquear la navegación ni interferir
 * con los botones de confirmación del sistema.
 */
class AppProtectionAccessibilityMonitor(
    private val protectionMonitor: ProtectionMonitor
) {

    enum class SecurityObservationType {
        NONE,
        UNINSTALL_SELF_PROMPT,
        DEVICE_ADMIN_DEACTIVATION,
        ACCESSIBILITY_PERMISSION_SCREEN,
        SECURITY_SETTINGS_NAVIGATION
    }

    data class SecurityObservation(
        val type: SecurityObservationType,
        val packageName: String,
        val reason: String
    )

    companion object {
        private const val TAG = "AppProtAccessMonitor"
        private const val OWN_PACKAGE = "com.adbutility.guard"
        private const val CHECK_THROTTLE_MS = 2000L

        private val INSTALLER_OR_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.transsion.settings",
            "com.samsung.android.settings",
            "com.miui.settings",
            "com.oplus.settings",
            "com.coloros.settings",
            "com.motorola.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.transsion.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.oplus.appinstaller"
        )

        private val APP_IDENTIFIERS = listOf(
            "adbutility guard",
            "adbutility",
            "com.adbutility.guard"
        )

        private val UNINSTALL_CUES = listOf(
            "desinstalar",
            "uninstall",
            "quieres desinstalar",
            "do you want to uninstall",
            "eliminar esta app",
            "remove this app"
        )

        private val ADMIN_DEACTIVATION_CUES = listOf(
            "administracion del dispositivo",
            "administrador de dispositivos",
            "aplicacion de administracion",
            "desactivar esta aplicacion de administracion",
            "desactivar y desinstalar",
            "device admin",
            "device administrator",
            "deactivate this device admin",
            "deactivate and uninstall",
            "remove active admin"
        )

        private val ACCESSIBILITY_SCREEN_CUES = listOf(
            "accesibilidad",
            "accessibility",
            "servicio de accesibilidad",
            "accessibility service",
            "usar adbutility guard",
            "use adbutility guard",
            "ajustes restringidos",
            "restricted settings"
        )

        private val SECURITY_NAV_CUES = listOf(
            "deviceadmin",
            "accessibilitysettings",
            "installedappdetails",
            "aplicaciones de administracion",
            "device admin apps"
        )

        /**
         * Analiza de forma pura las señales combinadas (paquete, clase, textos e IDs)
         * para detectar pantallas relevantes sin depender de una sola pista aislada.
         */
        fun inspectSignals(
            packageName: String,
            className: String,
            normalizedText: String,
            viewIds: List<String> = emptyList()
        ): SecurityObservation {
            if (packageName.isBlank() || packageName == OWN_PACKAGE) {
                return SecurityObservation(SecurityObservationType.NONE, packageName, "")
            }

            val lowerPkg = packageName.lowercase()
            val lowerClass = className.lowercase()
            val isRelevantHost = lowerPkg in INSTALLER_OR_SETTINGS_PACKAGES ||
                lowerPkg.contains("settings") ||
                lowerPkg.contains("packageinstaller")

            if (!isRelevantHost) {
                return SecurityObservation(SecurityObservationType.NONE, packageName, "")
            }

            val referencesApp = APP_IDENTIFIERS.any { normalizedText.contains(it) }
            val hasUninstallCue = UNINSTALL_CUES.any { normalizedText.contains(it) }
            val hasAdminCue = ADMIN_DEACTIVATION_CUES.any { normalizedText.contains(it) } ||
                lowerClass.contains("deviceadmin")
            val hasAccessibilityCue = ACCESSIBILITY_SCREEN_CUES.any { normalizedText.contains(it) } ||
                lowerClass.contains("accessibility")
            val hasSecurityNavCue = SECURITY_NAV_CUES.any {
                lowerClass.contains(it) || normalizedText.contains(it) || viewIds.any { id -> id.contains(it) }
            }

            return when {
                referencesApp && hasAdminCue -> SecurityObservation(
                    type = SecurityObservationType.DEVICE_ADMIN_DEACTIVATION,
                    packageName = packageName,
                    reason = "Pantalla de administración de dispositivo para la app observada."
                )

                referencesApp && hasUninstallCue -> SecurityObservation(
                    type = SecurityObservationType.UNINSTALL_SELF_PROMPT,
                    packageName = packageName,
                    reason = "Pantalla o diálogo de desinstalación de la app observado."
                )

                referencesApp && hasAccessibilityCue -> SecurityObservation(
                    type = SecurityObservationType.ACCESSIBILITY_PERMISSION_SCREEN,
                    packageName = packageName,
                    reason = "Pantalla de permisos de accesibilidad de la app observada."
                )

                hasAdminCue || (hasSecurityNavCue && (hasAccessibilityCue || hasUninstallCue)) -> SecurityObservation(
                    type = SecurityObservationType.SECURITY_SETTINGS_NAVIGATION,
                    packageName = packageName,
                    reason = "Navegación hacia configuración de seguridad relevante."
                )

                else -> SecurityObservation(SecurityObservationType.NONE, packageName, "")
            }
        }
    }

    private var lastCheckAtMs: Long = 0L

    /**
     * Observa pasivamente el evento de accesibilidad. Jamás bloquea la navegación ni botones.
     */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ) {
        val pkgName = event.packageName?.toString()?.trim().orEmpty()
        if (pkgName.isEmpty() || pkgName == OWN_PACKAGE) return

        val now = System.currentTimeMillis()
        if ((now - lastCheckAtMs) < CHECK_THROTTLE_MS &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val snapshot = UpdateTextDetector.captureSnapshot(event, rootNode)
        val observation = inspectSignals(
            packageName = snapshot.packageName,
            className = snapshot.className,
            normalizedText = snapshot.normalizedText,
            viewIds = snapshot.viewIds
        )

        if (observation.type != SecurityObservationType.NONE) {
            lastCheckAtMs = now
            Log.d(TAG, "Observación pasiva [${observation.type}]: ${observation.reason}")
            protectionMonitor.repository.recordSecurityNavigationObservation(observation.type.name)

            // Reevaluar estado real sin interferir ni bloquear al usuario
            protectionMonitor.evaluateProtection(notifyOnRegression = true)
        }
    }
}

