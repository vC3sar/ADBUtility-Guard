package com.adbutility.guard.protection

import com.adbutility.guard.accessibility.AppProtectionAccessibilityMonitor
import com.adbutility.guard.accessibility.AppProtectionAccessibilityMonitor.SecurityObservationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para la lógica central de App Protection:
 * derivación real de estados, detección de estados incorrectos y observación pasiva de seguridad.
 */
class AppProtectionTest {

    @Test
    fun fullProtection_requiresAdminServiceAndBasicConfig() {
        val snapshot = ProtectionState.derive(
            isDeviceAdminActive = true,
            isAccessibilityServiceActive = true,
            areNotificationsEnabled = true,
            isBasicProtectionConfigured = true
        )

        assertEquals(ProtectionLevel.FULL_PROTECTION, snapshot.level)
        assertTrue(snapshot.isSecuritySupervisionActive)
        assertFalse(snapshot.hasPendingDeactivationNotice)
    }

    @Test
    fun partialProtection_whenAdminActiveButServiceInactive() {
        val snapshot = ProtectionState.derive(
            isDeviceAdminActive = true,
            isAccessibilityServiceActive = false,
            areNotificationsEnabled = true,
            isBasicProtectionConfigured = true,
            isServiceDisabledNoticePending = true
        )

        assertEquals(ProtectionLevel.PARTIAL_PROTECTION, snapshot.level)
        assertFalse(snapshot.isSecuritySupervisionActive)
        assertTrue(snapshot.hasPendingDeactivationNotice)
    }

    @Test
    fun partialProtection_whenAdminDeactivatedWhileServiceActive() {
        val snapshot = ProtectionState.derive(
            isDeviceAdminActive = false,
            isAccessibilityServiceActive = true,
            areNotificationsEnabled = true,
            isBasicProtectionConfigured = true,
            isAdminDisabledNoticePending = true
        )

        assertEquals(ProtectionLevel.PARTIAL_PROTECTION, snapshot.level)
        assertTrue(snapshot.isAdminDisabledNoticePending)
        assertTrue(snapshot.hasPendingDeactivationNotice)
    }

    @Test
    fun unprotected_whenBothAdminAndServiceInactive() {
        val snapshot = ProtectionState.derive(
            isDeviceAdminActive = false,
            isAccessibilityServiceActive = false,
            areNotificationsEnabled = true,
            isBasicProtectionConfigured = true
        )

        assertEquals(ProtectionLevel.UNPROTECTED, snapshot.level)
        assertFalse(snapshot.isSecuritySupervisionActive)
    }

    @Test
    fun incorrectBasicConfig_preventsFullProtectionAndMarksSupervisionStandby() {
        val snapshot = ProtectionState.derive(
            isDeviceAdminActive = true,
            isAccessibilityServiceActive = true,
            areNotificationsEnabled = true,
            isBasicProtectionConfigured = false
        )

        assertEquals(ProtectionLevel.PARTIAL_PROTECTION, snapshot.level)
        assertFalse(snapshot.isSecuritySupervisionActive)
    }

    @Test
    fun accessibilityMonitor_detectsUninstallSelfPromptPassively() {
        val obs = AppProtectionAccessibilityMonitor.inspectSignals(
            packageName = "com.google.android.packageinstaller",
            className = "com.android.packageinstaller.UninstallerActivity",
            normalizedText = "adbutility guard quieres desinstalar esta aplicacion cancelar aceptar"
        )
        assertEquals(SecurityObservationType.UNINSTALL_SELF_PROMPT, obs.type)
    }

    @Test
    fun accessibilityMonitor_detectsDeviceAdminDeactivationScreen() {
        val obs = AppProtectionAccessibilityMonitor.inspectSignals(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd",
            normalizedText = "aplicacion de administracion del dispositivo adbutility guard desactivar esta aplicacion de administracion"
        )
        assertEquals(SecurityObservationType.DEVICE_ADMIN_DEACTIVATION, obs.type)
    }

    @Test
    fun accessibilityMonitor_detectsAccessibilityPermissionScreen() {
        val obs = AppProtectionAccessibilityMonitor.inspectSignals(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            normalizedText = "servicio de accesibilidad usar adbutility guard service"
        )
        assertEquals(SecurityObservationType.ACCESSIBILITY_PERMISSION_SCREEN, obs.type)
    }

    @Test
    fun accessibilityMonitor_ignoresUnrelatedAppUninstall() {
        val obs = AppProtectionAccessibilityMonitor.inspectSignals(
            packageName = "com.google.android.packageinstaller",
            className = "com.android.packageinstaller.UninstallerActivity",
            normalizedText = "whatsapp quieres desinstalar esta aplicacion cancelar aceptar"
        )
        assertEquals(SecurityObservationType.NONE, obs.type)
    }
}

