package com.adbutility.guard.updateguard

/**
 * Categoría de operación determinada por el motor inteligente.
 */
enum class UpdateOperationCategory {
    SYSTEM_UPDATE,
    APP_UPDATE,
    APK_INSTALL,
    UNKNOWN
}

/**
 * Resultado interno de clasificación contextual.
 */
enum class ClassificationOutcome {
    CONFIRMED_SYSTEM_UPDATE,
    CONFIRMED_APP_OPERATION,
    UNCERTAIN
}

/**
 * Señales contextuales extraídas de la ventana para evaluación multi-criterio.
 */
data class ContextualSignals(
    val packageRole: UpdatePackageRegistry.PackageRole,
    val hasOsOtaTextCue: Boolean,
    val matchedOsOtaCues: List<String>,
    val hasOsOtaStructureCue: Boolean,
    val matchedStructureCues: List<String>,
    val hasDeviceRestartUpdateCue: Boolean,
    val matchedRestartCues: List<String>,
    val hasAppStoreContextCue: Boolean,
    val hasGmsOwnComponentUpdateCue: Boolean,
    val hasApkInstallContextCue: Boolean,
    val dangerousButtons: List<ActionButtonCandidate>,
    val safeButtons: List<ActionButtonCandidate>
)

/**
 * Resultado detallado del clasificador de riesgo.
 */
data class RiskClassificationResult(
    val category: UpdateOperationCategory,
    val outcome: ClassificationOutcome,
    val reason: String,
    val signals: ContextualSignals
)

/**
 * Clasificador contextual multi-señal que distingue actualizaciones reales del sistema/OTA/firmware
 * frente a actualizaciones de aplicaciones, Google Play Services, tiendas e instalaciones de APK.
 */
object UpdateRiskClassifier {

    // Indicadores fuertes de actualización del sistema operativo / firmware / OTA (ES + EN)
    private val OS_OTA_TEXT_CUES = listOf(
        "actualizacion del sistema",
        "actualizacion de sistema",
        "actualizacion de android",
        "actualizacion de firmware",
        "actualizacion ota",
        "actualizacion de seguridad",
        "parche de seguridad",
        "version del sistema",
        "nueva version del sistema",
        "nueva version de android",
        "actualizacion de hios",
        "nueva version de hios",
        "actualizacion de xos",
        "actualizacion de one ui",
        "actualizacion de hyperos",
        "actualizacion de miui",
        "actualizacion del dispositivo",
        "actualizacion de software",
        "actualizacion en linea",
        "actualizacion local",
        "sistema operativo",
        "system update",
        "android update",
        "os update",
        "firmware update",
        "ota update",
        "security update",
        "security patch",
        "hios update",
        "xos update",
        "new system version",
        "new android version",
        "software update",
        "online update",
        "local update",
        "operating system",
        "icono de actualizacion del sistema",
        "tu sistema lleva",
        "dias desactualizado",
        "dia desactualizado",
        "finalizar la actualizacion",
        "finalizar actualizacion",
        "your system is",
        "days out of date",
        "day out of date",
        "finish the update"
    )

    // Frases contextuales de reinicio específicamente vinculadas a finalizar/instalar una actualización del sistema
    private val SYSTEM_RESTART_UPDATE_CUES = listOf(
        "reiniciar para actualizar",
        "reiniciar para finalizar la actualizacion",
        "reiniciar para finalizar",
        "reiniciar para instalar",
        "reiniciar y actualizar",
        "reinicia el dispositivo",
        "reinicia tu telefono",
        "reinicia tu dispositivo",
        "se reiniciara para instalar",
        "se reiniciara el dispositivo",
        "finalizar la actualizacion",
        "finalizar actualizacion",
        "para completar la actualizacion",
        "restart to update",
        "restart to install",
        "restart to finish",
        "restart and install",
        "restart & install",
        "restart your device",
        "restart your phone",
        "will restart to install",
        "finish updating",
        "finish update",
        "complete the update",
        "reboot to install",
        "reboot to update"
    )

    // Clases de actividad o IDs de vista característicos de OTA/System Update (GMS, AOSP, TECNO/HiOS, OEMs)
    private val OS_OTA_STRUCTURE_CUES = listOf(
        "systemupdate",
        "system_update",
        "fota",
        "ota_update",
        "otaupdate",
        "hios_update",
        "os_update",
        "firmware_update",
        "dynsystem",
        "softwareupdate",
        "software_update",
        "updateinstallation",
        "restart_to_update",
        "system_upgrade",
        "gms.update",
        "update.phone.popupdialog"
    )

    // Indicadores contextuales de tienda de aplicaciones / actualización de apps normales
    private val APP_STORE_CONTEXT_CUES = listOf(
        "play store",
        "google play store",
        "palm store",
        "galaxy store",
        "novedades",
        "what's new",
        "whats new",
        "resenas",
        "reviews",
        "desinstalar",
        "uninstall",
        "acerca de esta app",
        "about this app",
        "acerca de esta aplicacion",
        "actualizar todas",
        "update all",
        "actualizaciones pendientes",
        "pending updates",
        "administrar apps",
        "manage apps",
        "tus apps y dispositivo",
        "manage apps & device"
    )

    // Indicadores propios de actualización normal de Google Play Services como app/componente
    private val GMS_OWN_APP_UPDATE_CUES = listOf(
        "actualizar servicios de google play",
        "actualiza los servicios de google play",
        "servicios de google play se estan actualizando",
        "servicios de google play requiere",
        "no actualizas los servicios de google play",
        "update google play services",
        "google play services is updating",
        "google play services won't run unless you update",
        "google play services requires"
    )

    // Indicadores contextuales de instalación o actualización de APK / ADB
    private val APK_INSTALL_CONTEXT_CUES = listOf(
        "quieres instalar esta aplicacion",
        "quieres instalar una actualizacion para esta aplicacion",
        "deseas instalar esta aplicacion",
        "deseas actualizar esta aplicacion",
        "aplicacion existente",
        "no requiere accesos especiales",
        "instalador de paquetes",
        "origenes desconocidos",
        "instalar via usb",
        "depuracion usb",
        "do you want to install this app",
        "do you want to update this app",
        "do you want to install an update to this existing application",
        "existing app",
        "does not require any special access",
        "package installer",
        "unknown sources",
        "install via usb",
        ".apk"
    )

    // Etiquetas de botones seguros (navegación / cancelación / aplazamiento) que JAMÁS deben bloquearse
    private val SAFE_BUTTON_TERMS = listOf(
        "cancelar",
        "cerrar",
        "descartar",
        "volver",
        "atras",
        "mas tarde",
        "ahora no",
        "posponer",
        "recordar mas tarde",
        "recordarmelo mas tarde",
        "omitir",
        "no gracias",
        "despues",
        "cancel",
        "close",
        "dismiss",
        "back",
        "later",
        "not now",
        "remind me later",
        "postpone",
        "skip",
        "no thanks"
    )

    // Etiquetas de botones peligrosos específicamente orientados a iniciar actualización o reinicio del sistema
    private val SYSTEM_DANGEROUS_BUTTON_TERMS = listOf(
        "reiniciar ahora",
        "reiniciar y actualizar",
        "reiniciar e instalar",
        "reiniciar para actualizar",
        "reiniciar dispositivo",
        "instalar actualizacion",
        "descargar e instalar",
        "actualizar ahora",
        "finalizar actualizacion",
        "actualizar dispositivo",
        "instalar ahora",
        "reiniciar",
        "restart now",
        "reboot now",
        "restart and install",
        "restart & install",
        "restart to update",
        "restart device",
        "install update",
        "download and install",
        "download & install",
        "update now",
        "finish update",
        "update device",
        "install now",
        "restart"
    )

    // Etiquetas genéricas ("actualizar", "instalar") que SOLO son peligrosas si ya existe contexto OS/OTA confirmado
    private val CONTEXTUAL_DANGEROUS_BUTTON_TERMS = listOf(
        "actualizar",
        "instalar",
        "continuar",
        "update",
        "install",
        "continue"
    )

    private val DANGEROUS_BUTTON_VIEW_IDS = listOf(
        "restart_button",
        "reboot_button",
        "install_update",
        "btn_restart",
        "btn_update_now",
        "download_install",
        "action_restart"
    )

    fun extractSignals(snapshot: ScreenContextSnapshot): ContextualSignals {
        val packageRole = UpdatePackageRegistry.classifyPackage(snapshot.packageName)
        val text = snapshot.normalizedText
        val lowerClass = snapshot.className.lowercase()

        val matchedOsTextCues = OS_OTA_TEXT_CUES.filter { text.contains(it) }
        val matchedRestartCues = SYSTEM_RESTART_UPDATE_CUES.filter { text.contains(it) }

        val matchedStructureCues = mutableListOf<String>()
        for (cue in OS_OTA_STRUCTURE_CUES) {
            if (lowerClass.contains(cue) || snapshot.viewIds.any { it.contains(cue) }) {
                matchedStructureCues.add(cue)
            }
        }

        val hasOsOtaTextCue = matchedOsTextCues.isNotEmpty()
        val hasDeviceRestartUpdateCue = matchedRestartCues.isNotEmpty()
        val hasOsOtaStructureCue = matchedStructureCues.isNotEmpty()

        val hasAppStoreContext = APP_STORE_CONTEXT_CUES.any { text.contains(it) }
        val hasGmsOwnComponentUpdate = GMS_OWN_APP_UPDATE_CUES.any { text.contains(it) }
        val hasApkInstallContext = APK_INSTALL_CONTEXT_CUES.any { text.contains(it) }

        val safeButtons = mutableListOf<ActionButtonCandidate>()
        val dangerousButtons = mutableListOf<ActionButtonCandidate>()

        val osContextPresent = hasOsOtaTextCue || hasDeviceRestartUpdateCue || hasOsOtaStructureCue

        for (btn in snapshot.buttons) {
            if (!btn.boundsInScreen.isValid() || !btn.isEnabled) continue

            if (isSafeButton(btn)) {
                safeButtons.add(btn)
                continue
            }

            if (isSystemDangerousButton(btn, osContextPresent)) {
                dangerousButtons.add(btn)
            }
        }

        return ContextualSignals(
            packageRole = packageRole,
            hasOsOtaTextCue = hasOsOtaTextCue,
            matchedOsOtaCues = matchedOsTextCues,
            hasOsOtaStructureCue = hasOsOtaStructureCue,
            matchedStructureCues = matchedStructureCues,
            hasDeviceRestartUpdateCue = hasDeviceRestartUpdateCue,
            matchedRestartCues = matchedRestartCues,
            hasAppStoreContextCue = hasAppStoreContext,
            hasGmsOwnComponentUpdateCue = hasGmsOwnComponentUpdate,
            hasApkInstallContextCue = hasApkInstallContext,
            dangerousButtons = dangerousButtons,
            safeButtons = safeButtons
        )
    }

    fun classify(snapshot: ScreenContextSnapshot): RiskClassificationResult {
        val signals = extractSignals(snapshot)

        // 1. Verificar primero si el contexto corresponde claramente a instalación/actualización de APK o ADB
        if (signals.packageRole == UpdatePackageRegistry.PackageRole.PACKAGE_INSTALLER &&
            !signals.hasOsOtaTextCue &&
            !signals.hasDeviceRestartUpdateCue
        ) {
            return RiskClassificationResult(
                category = UpdateOperationCategory.APK_INSTALL,
                outcome = ClassificationOutcome.CONFIRMED_APP_OPERATION,
                reason = "Instalador de paquetes/ADB sin señales de actualización OTA ni del sistema.",
                signals = signals
            )
        }

        if (signals.hasApkInstallContextCue && !signals.hasOsOtaTextCue && !signals.hasDeviceRestartUpdateCue) {
            return RiskClassificationResult(
                category = UpdateOperationCategory.APK_INSTALL,
                outcome = ClassificationOutcome.CONFIRMED_APP_OPERATION,
                reason = "Contexto de instalación/actualización de archivo APK detectado.",
                signals = signals
            )
        }

        // 2. Verificar actualización normal de aplicación en Tienda (Play Store, Palm Store, etc.)
        if (signals.packageRole == UpdatePackageRegistry.PackageRole.APP_STORE &&
            !signals.hasOsOtaTextCue &&
            !signals.hasDeviceRestartUpdateCue &&
            !signals.hasOsOtaStructureCue
        ) {
            return RiskClassificationResult(
                category = UpdateOperationCategory.APP_UPDATE,
                outcome = ClassificationOutcome.CONFIRMED_APP_OPERATION,
                reason = "Operación normal en tienda de aplicaciones.",
                signals = signals
            )
        }

        if (signals.hasAppStoreContextCue && !signals.hasOsOtaTextCue && !signals.hasDeviceRestartUpdateCue) {
            return RiskClassificationResult(
                category = UpdateOperationCategory.APP_UPDATE,
                outcome = ClassificationOutcome.CONFIRMED_APP_OPERATION,
                reason = "Contexto de tienda de aplicaciones detectado sin señales de sistema operativo.",
                signals = signals
            )
        }

        // 3. Caso especial Google Play Services actualizándose como componente propio (sin OTA/reinicio)
        if (signals.hasGmsOwnComponentUpdateCue &&
            !signals.hasOsOtaTextCue &&
            !signals.hasDeviceRestartUpdateCue
        ) {
            return RiskClassificationResult(
                category = UpdateOperationCategory.APP_UPDATE,
                outcome = ClassificationOutcome.CONFIRMED_APP_OPERATION,
                reason = "Actualización normal de componente Google Play Services.",
                signals = signals
            )
        }

        // 4. Evaluación multi-señal coherente para SYSTEM_UPDATE
        // Requiere corroboración entre contexto de sistema/OTA/firmware y solicitud o acción de actualización/reinicio.
        val hasStrongSystemContext = when (signals.packageRole) {
            UpdatePackageRegistry.PackageRole.DEDICATED_OTA_UPDATER -> {
                // Incluso en un actualizador OTA dedicado, exigimos señales de contenido/estructura reales
                signals.hasOsOtaTextCue || signals.hasDeviceRestartUpdateCue || signals.hasOsOtaStructureCue
            }
            UpdatePackageRegistry.PackageRole.DUAL_PURPOSE_SYSTEM_OR_GMS -> {
                // En paquetes duales (GMS, Ajustes, SystemUI), exigimos combinación fuerte:
                // (texto OS/OTA o estructura OTA) Y (texto de reinicio para actualizar O botón de sistema específico)
                // O bien frase explícita de reinicio del dispositivo para finalizar actualización + botón explícito de reinicio/sistema
                val hasStrongTextCombo =
                    (signals.hasOsOtaTextCue && (signals.hasDeviceRestartUpdateCue || signals.dangerousButtons.any { isExplicitSystemButton(it) })) ||
                        (signals.hasOsOtaStructureCue && (signals.hasOsOtaTextCue || signals.hasDeviceRestartUpdateCue)) ||
                        (signals.hasDeviceRestartUpdateCue && signals.dangerousButtons.any { isExplicitSystemButton(it) })
                hasStrongTextCombo
            }
            else -> {
                // Otros paquetes: solo si combinan explícitamente texto OS/firmware + texto explícito de reinicio para actualizar
                signals.hasOsOtaTextCue && signals.hasDeviceRestartUpdateCue
            }
        }

        if (hasStrongSystemContext) {
            // Verificar regla de accesibilidad: si no hay botones peligrosos identificables con límites válidos,
            // no cubrir indiscriminadamente ni afirmar que se bloqueó (Caso 11 y Sección 7).
            if (signals.dangerousButtons.isEmpty()) {
                return RiskClassificationResult(
                    category = UpdateOperationCategory.UNKNOWN,
                    outcome = ClassificationOutcome.UNCERTAIN,
                    reason = "Contexto de actualización del sistema detectado pero sin botones accesibles identificables; no se bloquea de forma insegura.",
                    signals = signals
                )
            }

            return RiskClassificationResult(
                category = UpdateOperationCategory.SYSTEM_UPDATE,
                outcome = ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE,
                reason = buildSystemReason(signals),
                signals = signals
            )
        }

        // 5. Todo diálogo ambiguo, incompleto o palabra aislada ("Actualizar") -> UNKNOWN / UNCERTAIN
        return RiskClassificationResult(
            category = UpdateOperationCategory.UNKNOWN,
            outcome = ClassificationOutcome.UNCERTAIN,
            reason = "Señales insuficientes o ambiguas para confirmar actualización del sistema.",
            signals = signals
        )
    }

    private fun isSafeButton(button: ActionButtonCandidate): Boolean {
        val label = button.normalizedLabel
        if (label.isEmpty()) return false
        return SAFE_BUTTON_TERMS.any { term ->
            label == term || label.startsWith("$term ") || label.contains(term)
        }
    }

    private fun isExplicitSystemButton(button: ActionButtonCandidate): Boolean {
        val label = button.normalizedLabel
        val viewId = button.viewId
        if (DANGEROUS_BUTTON_VIEW_IDS.any { viewId.contains(it) }) return true
        return SYSTEM_DANGEROUS_BUTTON_TERMS.any { term ->
            label == term || label.contains(term)
        }
    }

    private fun isSystemDangerousButton(
        button: ActionButtonCandidate,
        osContextPresent: Boolean
    ): Boolean {
        if (isExplicitSystemButton(button)) return true
        if (!osContextPresent) return false

        val label = button.normalizedLabel
        return CONTEXTUAL_DANGEROUS_BUTTON_TERMS.any { term ->
            label == term
        }
    }

    private fun buildSystemReason(signals: ContextualSignals): String {
        val parts = mutableListOf<String>()
        parts.add("role=${signals.packageRole}")
        if (signals.matchedOsOtaCues.isNotEmpty()) {
            parts.add("osCues=${signals.matchedOsOtaCues.take(2)}")
        }
        if (signals.matchedRestartCues.isNotEmpty()) {
            parts.add("restartCues=${signals.matchedRestartCues.take(2)}")
        }
        if (signals.matchedStructureCues.isNotEmpty()) {
            parts.add("struct=${signals.matchedStructureCues.take(2)}")
        }
        parts.add("targets=${signals.dangerousButtons.map { it.normalizedLabel }}")
        return parts.joinToString("; ")
    }
}

