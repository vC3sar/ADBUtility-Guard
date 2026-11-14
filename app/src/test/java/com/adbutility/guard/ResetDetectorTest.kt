package com.adbutility.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de detección de pantallas de restablecimiento y DNS privado.
 * Cubre Google Pixel 6a (Android 15 / SubSettings), escenarios en español e inglés,
 * exclusión de restablecimiento de red benigno y filtrado de apps ajenas.
 */
class ResetDetectorTest {

    private val detector = ResetDetector()

    @Test
    fun testPixel6aResetScreenDetected() {
        val texts = listOf(
            "Restablecimiento",
            "Restablecer configuración de red móvil",
            "Restablecer Bluetooth y Wi‑Fi",
            "Restablecer preferencias de apps",
            "Borrar eSIMs",
            "Borrar espacio privado",
            "Borrar todo (restablecer configuración de fábrica)"
        )

        val result = detector.evaluateRaw(
            pkgName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            extractedTexts = texts
        )

        assertTrue("La pantalla de restablecimiento del Pixel 6a debe ser detectada", result.isTargetScreen)
        assertFalse("No es manipulación de DNS", result.isDnsTampering)
        assertEquals("com.android.settings", result.packageName)
        assertTrue("Debe reportar alta confianza", result.confidenceScore >= 90)
    }

    @Test
    fun testBenignNetworkResetScreenNotDetected() {
        val texts = listOf(
            "Restablecer configuración de red móvil",
            "Se restablecerán Wi-Fi, Bluetooth y datos móviles"
        )

        val result = detector.evaluateRaw(
            pkgName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            extractedTexts = texts
        )

        assertFalse("La pantalla benigna de sólo red NO debe ser detectada", result.isTargetScreen)
    }

    @Test
    fun testEnglishFactoryResetDetected() {
        val texts = listOf(
            "Reset options",
            "Reset Wi-Fi, mobile & Bluetooth",
            "Reset app preferences",
            "Erase all data (factory reset)"
        )

        val result = detector.evaluateRaw(
            pkgName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            extractedTexts = texts
        )

        assertTrue("Factory reset en inglés debe ser detectado", result.isTargetScreen)
        assertFalse("No es DNS", result.isDnsTampering)
    }

    @Test
    fun testDnsTamperingDetected() {
        val texts = listOf(
            "DNS privado",
            "Desactivado",
            "Automático",
            "Nombre de host del proveedor de DNS privado"
        )

        val result = detector.evaluateRaw(
            pkgName = "com.android.settings",
            className = "com.android.settings.network.PrivateDnsModeDialog",
            extractedTexts = texts
        )

        assertTrue("Modificación de DNS privado debe ser detectada", result.isTargetScreen)
        assertTrue("Es manipulación de DNS", result.isDnsTampering)
    }

    @Test
    fun testSistemaParentMenuDoesNotTrigger() {
        // En la pantalla padre "Sistema", existe el item "Restablecimiento",
        // pero NO hay opciones de fábrica ni borrado. Jamás debe disparar alerta.
        val texts = listOf(
            "Sistema",
            "Navegación del sistema",
            "Idiomas",
            "Teclado",
            "Gestos",
            "Fecha y hora",
            "Copia de seguridad",
            "Modo de reparación",
            "Ambiente seguro para la reparación del dispositivo",
            "Restablecimiento",
            "Traducción instantánea"
        )

        val result = detector.evaluateRaw(
            pkgName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            extractedTexts = texts
        )

        assertFalse("El menú padre 'Sistema' NO debe disparar advertencia de restablecimiento", result.isTargetScreen)
    }

    @Test
    fun testNonSettingsPackageIgnored() {
        val texts = listOf(
            "Borrar todo (restablecer configuración de fábrica)"
        )

        val result = detector.evaluateRaw(
            pkgName = "com.example.calculator",
            className = "com.example.calculator.MainActivity",
            extractedTexts = texts
        )

        assertFalse("Paquetes fuera de Ajustes deben ignorarse", result.isTargetScreen)
    }
}
