package com.adbutility.guard

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

/**
 * Motor de detección de pantallas de restablecimiento de fábrica y manipulación de DNS privado.
 * Analiza paquetes, actividades, textos y descripciones accesibles en español e inglés,
 * aplicando normalización ortográfica, reglas combinadas OEM y control de enfriamiento.
 */
class ResetDetector(private val preferencesManager: PreferencesManager? = null) {

    data class DetectionResult(
        val isTargetScreen: Boolean,
        val packageName: String,
        val activityName: String,
        val matchedRule: String,
        val confidenceScore: Int,
        val isDnsTampering: Boolean = false
    )

    companion object {
        private const val TAG = "ResetDetector"
        private const val MAX_TREE_DEPTH = 16
        private const val MAX_NODES_VISITED = 160

        // Paquetes objetivo de Ajustes
        private val TARGET_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.google.android.settings.intelligence",
            "com.samsung.android.settings",
            "com.samsung.android.settings.general",
            "com.miui.securitycenter",
            "com.miui.settings",
            "com.miui.cloudservice",
            "com.motorola.settings",
            "com.oplus.settings",
            "com.coloros.settings",
            "com.transsion.settings"
        )

        // Nombres de actividades conocidos de restablecimiento
        private val KNOWN_RESET_ACTIVITIES = setOf(
            "factoryresetactivity",
            "masterclear",
            "masterclearconfirm",
            "resetsettingsactivity",
            "settings\$factoryresetactivity",
            "settings\$masterclearactivity",
            "settings\$masterclearconfirmactivity",
            "deviceresetsettingsactivity",
            "resetdashboardfragment",
            "mainclearconfirm"
        )

        // Frases de restablecimiento en Español (sin tildes)
        private val RESET_PHRASES_ES = listOf(
            "borrar todos los datos",
            "borrar todo (restablecer configuracion de fabrica)",
            "borrar todo restablecer configuracion de fabrica",
            "restablecer datos de fabrica",
            "restablecimiento de fabrica",
            "restablecer de fabrica",
            "restablecer la configuracion de fabrica",
            "restablecer configuracion de fabrica",
            "restablecer valores de fabrica",
            "volver al estado de fabrica",
            "eliminar todos los datos",
            "borrar todo del telefono",
            "se borraran todos los datos",
            "borrar todo el contenido"
        )

        // Frases de restablecimiento en Inglés
        private val RESET_PHRASES_EN = listOf(
            "factory data reset",
            "factory reset",
            "erase all data",
            "erase all data (factory reset)",
            "reset phone to factory",
            "delete all data (factory reset)",
            "all data will be erased",
            "master clear"
        )

        // Indicadores específicos del diálogo/pantalla de modificación de DNS Privado
        private val DNS_TRIGGER_TERMS = listOf(
            "desactivado",
            "off",
            "automatico",
            "automatic",
            "nombre de host del proveedor",
            "nombre de host",
            "provider hostname",
            "proveedor de dns",
            "dns provider",
            "introducir el nombre de host",
            "enter hostname"
        )

        // Términos benignos que deben excluirse si no contienen referencias a fábrica
        private val BENIGN_EXCLUSIONS = listOf(
            "restablecer ajustes de red",
            "restablecer configuracion de red",
            "restablecer preferencias de aplicaciones",
            "restablecer bluetooth",
            "reset network settings",
            "reset app preferences",
            "reset mobile network",
            "reset wifi"
        )
    }

    private var lastTriggerTime: Long = 0L
    private var lastScreenSignature: String = ""

    /**
     * Evalúa si el evento de accesibilidad o el árbol de nodos activo corresponde a una
     * pantalla de restablecimiento o a un intento de modificar/desactivar el DNS privado.
     */
    fun evaluate(event: AccessibilityEvent? = null, rootNode: AccessibilityNodeInfo?): DetectionResult {
        val isEffectiveProtection = preferencesManager?.isEffectiveProtectionActive() ?: true
        if (!isEffectiveProtection) {
            return DetectionResult(false, "", "", "protection_disabled", 0)
        }

        val pkgName = event?.packageName?.toString() ?: rootNode?.packageName?.toString() ?: ""
        val className = event?.className?.toString() ?: rootNode?.className?.toString() ?: ""

        // Ignorar eventos generados por nuestra propia aplicación
        if (pkgName == "com.adbutility.guard" || pkgName.isEmpty()) {
            return DetectionResult(false, pkgName, className, "ignored_own_package", 0)
        }

        // Extracción de textos del árbol de nodos de accesibilidad
        val extractedTexts = mutableListOf<String>()
        var foundDnsViewId = false

        if (rootNode != null) {
            foundDnsViewId = extractTextsFromNode(rootNode, extractedTexts, depth = 0, count = IntArray(1))
        }

        // Agregar textos del propio evento si está presente
        event?.text?.forEach { charSeq ->
            charSeq?.toString()?.let { extractedTexts.add(it) }
        }
        event?.contentDescription?.toString()?.let { extractedTexts.add(it) }

        val cooldownSec = preferencesManager?.cooldownSeconds ?: 0
        return evaluateRaw(
            pkgName = pkgName,
            className = className,
            extractedTexts = extractedTexts,
            foundDnsViewId = foundDnsViewId,
            isProtectionActive = true,
            cooldownSeconds = cooldownSec
        )
    }

    /**
     * Motor puro de evaluación de contenido textual, nombres de actividad y paquetes.
     * Desacoplado del entorno de accesibilidad para alta testeabilidad y velocidad.
     */
    fun evaluateRaw(
        pkgName: String,
        className: String,
        extractedTexts: List<String>,
        foundDnsViewId: Boolean = false,
        isProtectionActive: Boolean = true,
        cooldownSeconds: Int = 0
    ): DetectionResult {
        if (!isProtectionActive) {
            return DetectionResult(false, "", "", "protection_disabled", 0)
        }

        if (pkgName == "com.adbutility.guard" || pkgName.isEmpty()) {
            return DetectionResult(false, pkgName, className, "ignored_own_package", 0)
        }

        // Validar si el paquete pertenece a Ajustes del sistema
        val isTargetPkg = TARGET_PACKAGES.contains(pkgName) ||
                pkgName.endsWith(".settings") ||
                pkgName.contains(".settings.")

        if (!isTargetPkg) {
            return DetectionResult(false, pkgName, className, "non_settings_package", 0)
        }

        val now = System.currentTimeMillis()
        val cooldownMillis = cooldownSeconds * 1000L
        val isCooldownActive = (now - lastTriggerTime) < cooldownMillis

        val lowerClassName = className.lowercase(Locale.ROOT)

        // Normalizar texto (minúsculas y eliminación de acentos diacríticos)
        val rawCombined = extractedTexts.joinToString(" ")
        val normalizedContent = normalizeText(rawCombined)

        Log.d(TAG, "Inspección: pkg=$pkgName, class=$className, textos_len=${normalizedContent.length}")

        // ==========================================
        // 1. DETECCIÓN DE MODIFICACIÓN DE DNS PRIVADO
        // ==========================================
        val hasDnsMention = normalizedContent.contains("dns privado") ||
                normalizedContent.contains("private dns") ||
                foundDnsViewId ||
                lowerClassName.contains("privatedns")

        if (hasDnsMention) {
            // Verificar si estamos dentro del diálogo o menú de modificación/desactivación
            var isDnsModificationScreen = foundDnsViewId || lowerClassName.contains("privatedns")
            var matchedDnsOption = ""

            for (term in DNS_TRIGGER_TERMS) {
                if (normalizedContent.contains(term)) {
                    isDnsModificationScreen = true
                    matchedDnsOption = term
                    break
                }
            }

            if (isDnsModificationScreen) {
                val currentSignature = "$pkgName|dns_tampering|$matchedDnsOption"
                if (isCooldownActive && currentSignature == lastScreenSignature) {
                    Log.d(TAG, "Advertencia DNS suprimida por enfriamiento.")
                    return DetectionResult(false, pkgName, className, "cooldown_suppressed", 50)
                }

                lastTriggerTime = now
                lastScreenSignature = currentSignature

                Log.i(TAG, "¡Intento de modificar/quitar DNS privado detectado! Opción: '$matchedDnsOption' en $pkgName")
                return DetectionResult(
                    isTargetScreen = true,
                    packageName = pkgName,
                    activityName = className,
                    matchedRule = "DNS Privado: $matchedDnsOption",
                    confidenceScore = 95,
                    isDnsTampering = true
                )
            }
        }

        // ==========================================
        // 2. DETECCIÓN DE RESTABLECIMIENTO DE FÁBRICA
        // ==========================================
        var hasResetKeyword = false
        var matchedKeyword = ""

        for (phrase in RESET_PHRASES_ES) {
            if (normalizedContent.contains(phrase)) {
                hasResetKeyword = true
                matchedKeyword = phrase
                break
            }
        }

        if (!hasResetKeyword) {
            for (phrase in RESET_PHRASES_EN) {
                if (normalizedContent.contains(phrase)) {
                    hasResetKeyword = true
                    matchedKeyword = phrase
                    break
                }
            }
        }

        if (hasResetKeyword) {
            // Filtrar falsos positivos de red o apps cuando no hay opción real de fábrica
            var isBenign = false
            for (exclusion in BENIGN_EXCLUSIONS) {
                if (normalizedContent.contains(exclusion)) {
                    val containsRealFactoryTerm = normalizedContent.contains("fabrica") ||
                            normalizedContent.contains("factory") ||
                            normalizedContent.contains("borrar todos") ||
                            normalizedContent.contains("borrar todo (restablecer") ||
                            (normalizedContent.contains("borrar todo") && normalizedContent.contains("fabrica")) ||
                            normalizedContent.contains("erase all")
                    if (!containsRealFactoryTerm) {
                        isBenign = true
                        break
                    }
                }
            }

            if (isBenign) {
                Log.d(TAG, "Descartado: restablecimiento benigno.")
                return DetectionResult(false, pkgName, className, "benign_reset_screen", 0)
            }

            val currentSignature = "$pkgName|$className|$matchedKeyword"
            if (isCooldownActive && currentSignature == lastScreenSignature) {
                Log.d(TAG, "Advertencia restablecimiento suprimida por enfriamiento.")
                return DetectionResult(false, pkgName, className, "cooldown_suppressed", 50)
            }

            lastTriggerTime = now
            lastScreenSignature = currentSignature

            var activityMatch = false
            for (known in KNOWN_RESET_ACTIVITIES) {
                if (lowerClassName.contains(known)) {
                    activityMatch = true
                    break
                }
            }

            val confidence = if (activityMatch) 98 else 90
            Log.i(TAG, "¡Detección de restablecimiento positiva! Coincidencia: '$matchedKeyword' en $pkgName ($className)")
            return DetectionResult(
                isTargetScreen = true,
                packageName = pkgName,
                activityName = className,
                matchedRule = "Restablecimiento: $matchedKeyword",
                confidenceScore = confidence,
                isDnsTampering = false
            )
        }

        // Comprobación por actividad directa
        for (known in KNOWN_RESET_ACTIVITIES) {
            if (lowerClassName.contains(known)) {
                val currentSignature = "$pkgName|$className|activity_direct"
                if (!isCooldownActive || currentSignature != lastScreenSignature) {
                    lastTriggerTime = now
                    lastScreenSignature = currentSignature
                    Log.i(TAG, "Detección positiva por actividad directa: $lowerClassName")
                    return DetectionResult(
                        isTargetScreen = true,
                        packageName = pkgName,
                        activityName = className,
                        matchedRule = "Actividad: $lowerClassName",
                        confidenceScore = 92,
                        isDnsTampering = false
                    )
                }
            }
        }

        return DetectionResult(false, pkgName, className, "no_match", 0)
    }

    private fun normalizeText(input: String): String {
        val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }

    private fun extractTextsFromNode(
        node: AccessibilityNodeInfo,
        accumulator: MutableList<String>,
        depth: Int,
        count: IntArray
    ): Boolean {
        if (depth > MAX_TREE_DEPTH || count[0] > MAX_NODES_VISITED) {
            return false
        }
        count[0]++

        var dnsViewFound = false

        node.viewIdResourceName?.let { id ->
            val lowerId = id.lowercase(Locale.ROOT)
            if (lowerId.contains("private_dns") || lowerId.contains("privatedns")) {
                dnsViewFound = true
            }
        }

        node.text?.toString()?.trim()?.let {
            if (it.isNotEmpty()) accumulator.add(it)
        }

        node.contentDescription?.toString()?.trim()?.let {
            if (it.isNotEmpty()) accumulator.add(it)
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            if (extractTextsFromNode(child, accumulator, depth + 1, count)) {
                dnsViewFound = true
            }
            try {
                @Suppress("DEPRECATION")
                child.recycle()
            } catch (_: Exception) {
            }
        }

        return dnsViewFound
    }

    fun resetCooldown() {
        lastTriggerTime = 0L
        lastScreenSignature = ""
    }
}
