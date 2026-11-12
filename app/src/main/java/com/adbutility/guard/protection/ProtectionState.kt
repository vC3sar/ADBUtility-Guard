package com.adbutility.guard.protection

/**
 * Niveles centralizados de protección del dispositivo.
 */
enum class ProtectionLevel {
    /** Administración del dispositivo y servicio de accesibilidad activos y configurados. */
    FULL_PROTECTION,

    /** Alguna capa de protección está activa mientras otra requiere atención. */
    PARTIAL_PROTECTION,

    /** Sin protección esencial activa (administración y servicio desactivados o deshabilitados). */
    UNPROTECTED
}

/**
 * Instantánea real del estado de App Protection derivada únicamente de comprobaciones del sistema.
 */
data class ProtectionSnapshot(
    val level: ProtectionLevel,
    val isDeviceAdminActive: Boolean,
    val isAccessibilityServiceActive: Boolean,
    val areNotificationsEnabled: Boolean,
    val isBasicProtectionConfigured: Boolean,
    val isSecuritySupervisionActive: Boolean,
    val isAdminDisabledNoticePending: Boolean,
    val isServiceDisabledNoticePending: Boolean,
    val evaluatedAtMs: Long
) {
    val hasPendingDeactivationNotice: Boolean
        get() = isAdminDisabledNoticePending || isServiceDisabledNoticePending
}

/**
 * Derivador puro del estado general de protección.
 * Nunca confía en flags almacenados como fuente única de verdad.
 */
object ProtectionState {

    fun derive(
        isDeviceAdminActive: Boolean,
        isAccessibilityServiceActive: Boolean,
        areNotificationsEnabled: Boolean,
        isBasicProtectionConfigured: Boolean,
        isAdminDisabledNoticePending: Boolean = false,
        isServiceDisabledNoticePending: Boolean = false,
        evaluatedAtMs: Long = System.currentTimeMillis()
    ): ProtectionSnapshot {
        val level = when {
            isDeviceAdminActive && isAccessibilityServiceActive && isBasicProtectionConfigured ->
                ProtectionLevel.FULL_PROTECTION

            isDeviceAdminActive || isAccessibilityServiceActive ->
                ProtectionLevel.PARTIAL_PROTECTION

            else ->
                ProtectionLevel.UNPROTECTED
        }

        val supervisionActive = isAccessibilityServiceActive && isBasicProtectionConfigured

        return ProtectionSnapshot(
            level = level,
            isDeviceAdminActive = isDeviceAdminActive,
            isAccessibilityServiceActive = isAccessibilityServiceActive,
            areNotificationsEnabled = areNotificationsEnabled,
            isBasicProtectionConfigured = isBasicProtectionConfigured,
            isSecuritySupervisionActive = supervisionActive,
            isAdminDisabledNoticePending = isAdminDisabledNoticePending,
            isServiceDisabledNoticePending = isServiceDisabledNoticePending,
            evaluatedAtMs = evaluatedAtMs
        )
    }
}

