package com.adbutility.guard.updateguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias obligatorias del motor Smart Update Detection (Sección 14).
 * Cubren los 12 escenarios exigidos entre actualizaciones reales del sistema,
 * compatibilidad con Google Play Services/Store, instalaciones APK/ADB y casos ambiguos.
 */
class SmartUpdateDetectionTest {

    private val detector = SmartUpdateDetection()

    private fun button(
        label: String,
        viewId: String = "",
        left: Int = 600,
        top: Int = 1800,
        right: Int = 980,
        bottom: Int = 1920
    ): ActionButtonCandidate {
        return ActionButtonCandidate(
            rawLabel = label,
            normalizedLabel = UpdateTextDetector.normalizeText(label),
            contentDescription = "",
            viewId = viewId.lowercase(),
            className = "android.widget.Button",
            boundsInScreen = NodeBounds(left, top, right, bottom),
            isClickable = true,
            isEnabled = true
        )
    }

    private fun snapshot(
        pkg: String,
        cls: String,
        texts: List<String>,
        viewIds: List<String> = emptyList(),
        buttons: List<ActionButtonCandidate> = emptyList()
    ): ScreenContextSnapshot {
        val normalized = UpdateTextDetector.normalizeText(texts.joinToString(" "))
        return ScreenContextSnapshot(
            packageName = pkg,
            className = cls,
            eventType = 32, // TYPE_WINDOW_STATE_CHANGED
            rawTexts = texts,
            normalizedText = normalized,
            viewIds = viewIds.map { it.lowercase() },
            buttons = buttons
        )
    }

    // 1. Aviso de Android que solicita reiniciar para finalizar una actualización
    @Test
    fun test01_androidRestartToFinishSystemUpdate_isConfirmedAndBlocked() {
        val snap = snapshot(
            pkg = "com.android.settings",
            cls = "com.android.settings.SubSettings",
            texts = listOf(
                "Actualización del sistema",
                "Reinicia el dispositivo para completar la instalación de la actualización de seguridad.",
                "Más tarde",
                "Reiniciar ahora"
            ),
            buttons = listOf(
                button("Más tarde", left = 100, right = 450),
                button("Reiniciar ahora", viewId = "com.android.settings:id/restart_button")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.SYSTEM_UPDATE, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE, decision.outcome)
        assertTrue(decision.shouldBlock)
        assertEquals(1, decision.dangerousButtons.size)
        assertEquals("reiniciar ahora", decision.dangerousButtons.first().normalizedLabel)
    }

    // 2. Aviso de actualización OTA de TECNO (HiOS / Android 15)
    @Test
    fun test02_tecnoHiosOtaUpdate_isConfirmedAndBlocked() {
        val snap = snapshot(
            pkg = "com.transsion.systemupdate",
            cls = "com.transsion.systemupdate.ui.OtaMainActivity",
            texts = listOf(
                "Actualización del sistema",
                "Nueva versión de HiOS V15.0 disponible. Actualización de firmware y parche de seguridad.",
                "Cancelar",
                "Descargar e instalar"
            ),
            viewIds = listOf("com.transsion.systemupdate:id/ota_update_title"),
            buttons = listOf(
                button("Cancelar", left = 80, right = 400),
                button("Descargar e instalar", viewId = "com.transsion.systemupdate:id/btn_update_now")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.SYSTEM_UPDATE, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE, decision.outcome)
        assertTrue(decision.shouldBlock)
        assertEquals(1, decision.dangerousButtons.size)
        assertEquals("descargar e instalar", decision.dangerousButtons.first().normalizedLabel)
    }

    // 3. Aviso de actualización del sistema procedente de Google Play Services
    @Test
    fun test03_googlePlayServicesSystemOtaPrompt_isConfirmedAndBlocked() {
        val snap = snapshot(
            pkg = "com.google.android.gms",
            cls = "com.google.android.gms.update.SystemUpdateActivity",
            texts = listOf(
                "System update available",
                "Android 15 security update is ready. Restart your device to install.",
                "Later",
                "Restart now"
            ),
            viewIds = listOf("com.google.android.gms:id/system_update_status"),
            buttons = listOf(
                button("Later", left = 100, right = 400),
                button("Restart now", viewId = "com.google.android.gms:id/action_restart")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.SYSTEM_UPDATE, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE, decision.outcome)
        assertTrue(decision.shouldBlock)
        assertEquals(1, decision.dangerousButtons.size)
        assertEquals("restart now", decision.dangerousButtons.first().normalizedLabel)
    }

    // 4. Actualización normal de Google Play Services (componente/app propia) -> PERMITIR
    @Test
    fun test04_googlePlayServicesNormalComponentUpdate_isAllowed() {
        val snap = snapshot(
            pkg = "com.google.android.gms",
            cls = "androidx.appcompat.app.AlertDialog",
            texts = listOf(
                "Actualizar Servicios de Google Play",
                "Esta aplicación no se ejecutará si no actualizas los Servicios de Google Play.",
                "Actualizar"
            ),
            buttons = listOf(
                button("Actualizar")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.APP_UPDATE, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_APP_OPERATION, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 5. Actualización de WhatsApp desde Play Store -> PERMITIR
    @Test
    fun test05_whatsappUpdateFromPlayStore_isAllowed() {
        val snap = snapshot(
            pkg = "com.android.vending",
            cls = "com.google.android.finsky.activities.MainActivity",
            texts = listOf(
                "WhatsApp Messenger",
                "Google Play",
                "Novedades",
                "Reseñas",
                "Desinstalar",
                "Actualizar"
            ),
            buttons = listOf(
                button("Desinstalar", left = 80, right = 450),
                button("Actualizar")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.APP_UPDATE, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_APP_OPERATION, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 6. Actualización de Chrome desde Play Store -> PERMITIR
    @Test
    fun test06_chromeUpdateFromPlayStore_isAllowed() {
        val snap = snapshot(
            pkg = "com.android.vending",
            cls = "com.google.android.finsky.activities.MainActivity",
            texts = listOf(
                "Google Chrome",
                "What's new",
                "Reviews",
                "Uninstall",
                "Update"
            ),
            buttons = listOf(
                button("Uninstall", left = 80, right = 450),
                button("Update")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.APP_UPDATE, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_APP_OPERATION, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 7. Instalación de APK -> PERMITIR
    @Test
    fun test07_apkInstallation_isAllowed() {
        val snap = snapshot(
            pkg = "com.google.android.packageinstaller",
            cls = "com.android.packageinstaller.InstallStaging",
            texts = listOf(
                "MiAplicacion.apk",
                "¿Quieres instalar esta aplicación?",
                "Cancelar",
                "Instalar"
            ),
            buttons = listOf(
                button("Cancelar", left = 100, right = 400),
                button("Instalar")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.APK_INSTALL, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_APP_OPERATION, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 8. Actualización de APK existente -> PERMITIR
    @Test
    fun test08_existingApkUpdate_isAllowed() {
        val snap = snapshot(
            pkg = "com.google.android.packageinstaller",
            cls = "com.android.packageinstaller.PackageInstallerActivity",
            texts = listOf(
                "¿Quieres instalar una actualización para esta aplicación existente?",
                "Cancelar",
                "Actualizar"
            ),
            buttons = listOf(
                button("Cancelar", left = 100, right = 400),
                button("Actualizar")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.APK_INSTALL, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_APP_OPERATION, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 9. Instalación mediante ADB -> PERMITIR
    @Test
    fun test09_adbInstall_isAllowed() {
        val snap = snapshot(
            pkg = "com.android.shell",
            cls = "com.miui.permcenter.install.AdbInstallActivity",
            texts = listOf(
                "Instalar vía USB",
                "¿Deseas instalar esta aplicación desde ADB?",
                "Denegar",
                "Instalar"
            ),
            buttons = listOf(
                button("Instalar")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.APK_INSTALL, decision.category)
        assertEquals(ClassificationOutcome.CONFIRMED_APP_OPERATION, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 10. Ventana desconocida con la palabra «Actualizar» aislada -> NO BLOQUEAR
    @Test
    fun test10_unknownWindowWithIsolatedUpdateWord_isUncertainAndNotBlocked() {
        val snap = snapshot(
            pkg = "com.example.randomapp",
            cls = "com.example.randomapp.MainActivity",
            texts = listOf(
                "Nueva versión disponible",
                "Actualizar"
            ),
            buttons = listOf(
                button("Actualizar")
            )
        )

        val decision = detector.evaluate(snap)
        assertEquals(UpdateOperationCategory.UNKNOWN, decision.category)
        assertEquals(ClassificationOutcome.UNCERTAIN, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 11. Diálogo de actualización sin botones accesibles -> NO BLOQUEAR si existe incertidumbre
    @Test
    fun test11_systemUpdateDialogWithoutAccessibleButtons_doesNotBlockBlindly() {
        val snap = snapshot(
            pkg = "com.transsion.systemupdate",
            cls = "com.transsion.systemupdate.ui.OtaDialog",
            texts = listOf(
                "Actualización del sistema HiOS",
                "Reiniciar para actualizar"
            ),
            buttons = emptyList() // El árbol no expone botones accesibles ni límites válidos
        )

        val decision = detector.evaluate(snap)
        assertEquals(ClassificationOutcome.UNCERTAIN, decision.outcome)
        assertFalse(decision.shouldBlock)
    }

    // 12. Desaparición del diálogo original mientras el overlay está activo -> deja de confirmar bloqueo
    @Test
    fun test12_originalDialogDisappears_transitionsFromBlockedToCleared() {
        val activeDialog = snapshot(
            pkg = "com.google.android.gms",
            cls = "com.google.android.gms.update.SystemUpdateActivity",
            texts = listOf(
                "Actualización del sistema",
                "Reinicia tu dispositivo para instalar la actualización.",
                "Reiniciar ahora"
            ),
            buttons = listOf(button("Reiniciar ahora"))
        )
        val firstDecision = detector.evaluate(activeDialog)
        assertTrue(firstDecision.shouldBlock)

        // Usuario cierra el diálogo / vuelve al launcher o pantalla normal de Ajustes
        val disappearedDialog = snapshot(
            pkg = "com.android.settings",
            cls = "com.android.settings.homepage.SettingsHomepageActivity",
            texts = listOf("Ajustes", "Red e Internet", "Dispositivos conectados", "Aplicaciones"),
            buttons = emptyList()
        )
        val afterDecision = detector.evaluate(disappearedDialog)
        assertFalse(afterDecision.shouldBlock)
        assertEquals(ClassificationOutcome.UNCERTAIN, afterDecision.outcome)
    }

    // 13. Diálogo real GMS PopupDialog observado en TECNO Android 15 (con o sin content-desc del ícono / className FrameLayout)
    @Test
    fun test13_tecnoGmsPopupDialog_withAndWithoutIconDesc_blocksRestartNowAndAllowsDismiss() {
        val sharedButtons = listOf(
            button(
                label = "Descartar",
                viewId = "com.google.android.gms:id/material3HorizontalButton3",
                left = 82,
                top = 1058,
                right = 352,
                bottom = 1154
            ),
            button(
                label = "Reiniciar ahora",
                viewId = "com.google.android.gms:id/material3HorizontalButton1",
                left = 368,
                top = 1051,
                right = 638,
                bottom = 1162
            )
        )

        val withIconAndClass = snapshot(
            pkg = "com.google.android.gms",
            cls = "com.google.android.gms.update.phone.PopupDialog",
            texts = listOf(
                "Imagen. Ícono de actualización del sistema.",
                "Reinicia el dispositivo ahora para finalizar la actualización",
                "Tu sistema lleva 39 días desactualizado.\n\nMás información",
                "Descartar",
                "Reiniciar ahora"
            ),
            viewIds = listOf(
                "com.google.android.gms:id/dialog_icon",
                "com.google.android.gms:id/dialogTitle",
                "com.google.android.gms:id/message",
                "com.google.android.gms:id/material3HorizontalButton3",
                "com.google.android.gms:id/material3HorizontalButton1"
            ),
            buttons = sharedButtons
        )

        val d1 = detector.evaluate(withIconAndClass)
        assertEquals(UpdateOperationCategory.SYSTEM_UPDATE, d1.category)
        assertEquals(ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE, d1.outcome)
        assertTrue(d1.shouldBlock)
        assertEquals(1, d1.dangerousButtons.size)
        assertEquals("Reiniciar ahora", d1.dangerousButtons.first().rawLabel)

        val withoutIconAndGenericRoot = snapshot(
            pkg = "com.google.android.gms",
            cls = "android.widget.FrameLayout",
            texts = listOf(
                "Reinicia el dispositivo ahora para finalizar la actualización",
                "Tu sistema lleva 39 días desactualizado.\n\nMás información",
                "Descartar",
                "Reiniciar ahora"
            ),
            viewIds = listOf(
                "com.google.android.gms:id/dialogTitle",
                "com.google.android.gms:id/message",
                "com.google.android.gms:id/material3HorizontalButton3",
                "com.google.android.gms:id/material3HorizontalButton1"
            ),
            buttons = sharedButtons
        )

        val d2 = detector.evaluate(withoutIconAndGenericRoot)
        assertEquals(UpdateOperationCategory.SYSTEM_UPDATE, d2.category)
        assertEquals(ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE, d2.outcome)
        assertTrue(d2.shouldBlock)
        assertEquals(1, d2.dangerousButtons.size)
        assertEquals("Reiniciar ahora", d2.dangerousButtons.first().rawLabel)
    }
}

