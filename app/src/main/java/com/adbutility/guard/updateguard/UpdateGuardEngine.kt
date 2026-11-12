package com.adbutility.guard.updateguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Coordinador principal del módulo Update Guard.
 * Conecta el servicio de accesibilidad existente con el motor inteligente de detección,
 * los escudos selectivos de botones, el aviso visual y el registro diagnóstico interno.
 */
class UpdateGuardEngine(context: Context) {

    companion object {
        private const val TAG = "UpdateGuardEngine"
        private const val OWN_PACKAGE = "com.adbutility.guard"
        private const val DIAG_DEDUP_MS = 3000L
    }

    val settings = UpdateGuardSettings(context)
    val eventLogger = UpdateEventLogger(context)
    val smartDetector = SmartUpdateDetection()
    val buttonOverlay = UpdateAccessibilityOverlay(context)
    val warningController = UpdateWarningController(context)

    private var activeSessionPackage: String = ""
    private var activeSessionSignature: String = ""

    private var lastDiagKey: String = ""
    private var lastDiagTimeMs: Long = 0L

    /**
     * Procesa un evento de accesibilidad (TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED, TYPE_VIEW_CLICKED).
     */
    fun onAccessibilityEvent(
        service: AccessibilityService,
        event: AccessibilityEvent,
        activeRoot: AccessibilityNodeInfo?
    ) {
        if (!settings.isUpdateGuardEnabled) {
            if (hasActiveProtection()) {
                clearProtectionOverlays()
            }
            return
        }

        val eventPkg = event.packageName?.toString()?.trim().orEmpty()
        if (eventPkg == OWN_PACKAGE) {
            return
        }

        // Resolver el nodo raíz real de la ventana objetivo (evitando leer nuestros propios overlays)
        val resolution = resolveTargetWindowRoot(service, eventPkg, activeRoot)
        val targetRoot = resolution.rootNode
        val ownsTargetRoot = resolution.mustRecycleRoot

        try {
            val effectivePkg = targetRoot?.packageName?.toString()?.trim().orEmpty()
                .ifEmpty { eventPkg }

            if (effectivePkg == OWN_PACKAGE || effectivePkg.isEmpty()) {
                return
            }

            // Si había una sesión protegida activa y el usuario cambió a otro paquete/launcher,
            // el diálogo original desapareció: limpiar overlays inmediatamente (Caso 12).
            if (hasActiveProtection() && activeSessionPackage.isNotEmpty() && effectivePkg != activeSessionPackage) {
                Log.i(TAG, "Ventana original ($activeSessionPackage) desapareció al cambiar a $effectivePkg. Limpiando overlays.")
                clearProtectionOverlays()
            }

            val snapshot = UpdateTextDetector.captureSnapshot(event, targetRoot)
            val decision = smartDetector.evaluate(snapshot)

            handleDecision(service, snapshot, decision)
        } catch (t: Throwable) {
            Log.e(TAG, "Error en UpdateGuardEngine: ${t.message}", t)
        } finally {
            if (ownsTargetRoot && targetRoot != null) {
                try {
                    @Suppress("DEPRECATION")
                    targetRoot.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Inspecciona inmediatamente la ventana actualmente visible al conectar/reconectar el servicio,
     * para proteger diálogos de actualización del sistema que ya estaban en pantalla.
     */
    fun evaluateCurrentWindowOnConnect(service: AccessibilityService) {
        if (!settings.isUpdateGuardEnabled) {
            return
        }

        val activeRoot = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        }

        val resolution = resolveTargetWindowRoot(service, eventPkg = "", activeRoot = activeRoot)
        val targetRoot = resolution.rootNode
        val ownsTargetRoot = resolution.mustRecycleRoot

        try {
            if (targetRoot == null) {
                return
            }

            val effectivePkg = targetRoot.packageName?.toString()?.trim().orEmpty()
            if (effectivePkg.isEmpty() || effectivePkg == OWN_PACKAGE) {
                return
            }

            val windowTitle = try {
                targetRoot.window?.title?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }

            var bestSnapshot = UpdateTextDetector.captureSnapshotFromRoot(targetRoot, windowTitle)
            var bestDecision = smartDetector.evaluate(bestSnapshot)
            Log.i(
                TAG,
                "Inspección inicial (activeRoot): pkg=$effectivePkg children=${targetRoot.childCount} texts=${bestSnapshot.rawTexts.size} btns=${bestSnapshot.buttons.size} dangerous=${bestDecision.dangerousButtons.size} outcome=${bestDecision.outcome} reason='${bestDecision.reason}'"
            )

            // Si activeRoot llegó vacío/sin botones o no confirmó bloqueo, revisar service.windows por si el diálogo está en otra ventana de mayor/menor capa
            if (!bestDecision.shouldBlock) {
                val windows = try {
                    service.windows
                } catch (_: Exception) {
                    null
                }
                if (windows != null) {
                    for (win in windows.sortedByDescending { it.layer }) {
                        if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                            win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                        ) {
                            continue
                        }
                        val winRoot = win.root ?: continue
                        try {
                            val winPkg = winRoot.packageName?.toString()?.trim().orEmpty()
                            if (winPkg.isEmpty() || winPkg == OWN_PACKAGE) {
                                continue
                            }
                            val winTitle = try {
                                win.title?.toString().orEmpty()
                            } catch (_: Exception) {
                                ""
                            }
                            val winSnap = UpdateTextDetector.captureSnapshotFromRoot(winRoot, winTitle)
                            val winDec = smartDetector.evaluate(winSnap)
                            Log.i(
                                TAG,
                                "Inspección inicial (win layer=${win.layer} type=${win.type}): pkg=$winPkg title='$winTitle' children=${winRoot.childCount} texts=${winSnap.rawTexts.size} btns=${winSnap.buttons.size} dangerous=${winDec.dangerousButtons.size} outcome=${winDec.outcome}"
                            )
                            if (winDec.shouldBlock) {
                                bestSnapshot = winSnap
                                bestDecision = winDec
                                break
                            }
                        } finally {
                            @Suppress("DEPRECATION")
                            try {
                                winRoot.recycle()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }

            handleDecision(service, bestSnapshot, bestDecision)
        } catch (t: Throwable) {
            Log.e(TAG, "Error en evaluateCurrentWindowOnConnect: ${t.message}", t)
        } finally {
            if (ownsTargetRoot && targetRoot != null) {
                try {
                    @Suppress("DEPRECATION")
                    targetRoot.recycle()
                } catch (_: Exception) {
                }
            }
            if (activeRoot != null && activeRoot !== targetRoot) {
                try {
                    @Suppress("DEPRECATION")
                    activeRoot.recycle()
                } catch (_: Exception) {
                }
            } else if (activeRoot != null && !ownsTargetRoot) {
                try {
                    @Suppress("DEPRECATION")
                    activeRoot.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Evalúa una decisión pura (útil también para pruebas integradas del motor).
     */
    internal fun handleDecision(
        service: AccessibilityService?,
        snapshot: ScreenContextSnapshot,
        decision: SmartDetectionDecision
    ) {
        when {
            decision.shouldBlock && decision.outcome == ClassificationOutcome.CONFIRMED_SYSTEM_UPDATE -> {
                val signatureChanged = decision.dialogSignature != activeSessionSignature
                if (signatureChanged) {
                    warningController.resetSession()
                }

                activeSessionPackage = decision.packageName
                activeSessionSignature = decision.dialogSignature

                val shieldApplied = buttonOverlay.applyButtonShields(
                    service = service,
                    targets = decision.dangerousButtons,
                    onShieldTapped = {
                        // Si el usuario vuelve a pulsar el área protegida, recordar el bloqueo sin dejar pasar el toque
                        warningController.showBlockedWarning(
                            service = service,
                            dialogSignature = decision.dialogSignature,
                            forceFromShieldTap = true
                        )
                    }
                )

                val warningShown = if (shieldApplied) {
                    warningController.showBlockedWarning(
                        service = service,
                        dialogSignature = decision.dialogSignature,
                        forceFromShieldTap = false
                    )
                } else {
                    false
                }

                val blockResult = if (shieldApplied) {
                    "BLOCKED_DANGEROUS_BUTTONS"
                } else {
                    "OVERLAY_REJECTED_OR_UNAVAILABLE"
                }

                logDiagnosticDedup(
                    packageName = decision.packageName,
                    category = decision.category,
                    classification = decision.outcome,
                    reason = decision.reason,
                    blockResult = blockResult,
                    overlayShown = shieldApplied || warningShown
                )
            }

            decision.outcome == ClassificationOutcome.CONFIRMED_APP_OPERATION -> {
                // Operación legítima de aplicación / Play Store / GMS / APK: permitir y limpiar si había sesión previa
                if (hasActiveProtection()) {
                    clearProtectionOverlays()
                }

                // Registrar diagnóstico únicamente si hubo mención a actualizar/instalar o botones relevantes
                if (snapshot.normalizedText.contains("actualiz") ||
                    snapshot.normalizedText.contains("update") ||
                    snapshot.normalizedText.contains("instal")
                ) {
                    logDiagnosticDedup(
                        packageName = decision.packageName,
                        category = decision.category,
                        classification = decision.outcome,
                        reason = decision.reason,
                        blockResult = "ALLOWED",
                        overlayShown = false
                    )
                }
            }

            else -> {
                // UNCERTAIN / UNKNOWN: si había un overlay activo en el mismo paquete y el diálogo ya no está, limpiar
                if (hasActiveProtection()) {
                    Log.i(TAG, "Diálogo de actualización ya no está presente en ${snapshot.packageName}. Limpiando protección.")
                    clearProtectionOverlays()
                }

                // Registrar diagnóstico interno cuando existan señales parciales para depuración de falsos positivos
                if (snapshot.normalizedText.contains("actualiz") ||
                    snapshot.normalizedText.contains("update") ||
                    snapshot.normalizedText.contains("reiniciar") ||
                    snapshot.normalizedText.contains("restart")
                ) {
                    val blockResult = if (decision.reason.contains("sin botones accesibles", ignoreCase = true)) {
                        "NOT_BLOCKED_NO_ACCESSIBLE_BUTTONS"
                    } else {
                        "UNCERTAIN_ALLOWED"
                    }
                    logDiagnosticDedup(
                        packageName = decision.packageName,
                        category = decision.category,
                        classification = decision.outcome,
                        reason = decision.reason,
                        blockResult = blockResult,
                        overlayShown = false
                    )
                }
            }
        }
    }

    @Synchronized
    fun clearProtectionOverlays() {
        buttonOverlay.clearShields()
        warningController.dismissWarning(resetSessionSignature = true)
        activeSessionPackage = ""
        activeSessionSignature = ""
    }

    fun hasActiveProtection(): Boolean {
        return buttonOverlay.isShieldActive() || warningController.isWarningVisible()
    }

    private data class RootResolution(
        val rootNode: AccessibilityNodeInfo?,
        val mustRecycleRoot: Boolean
    )

    private fun resolveTargetWindowRoot(
        service: AccessibilityService,
        eventPkg: String,
        activeRoot: AccessibilityNodeInfo?
    ): RootResolution {
        val activePkg = activeRoot?.packageName?.toString()?.trim().orEmpty()
        if (activeRoot != null && activePkg.isNotEmpty() && activePkg != OWN_PACKAGE) {
            return RootResolution(activeRoot, mustRecycleRoot = false)
        }

        // Si activeRoot pertenece a nuestro propio overlay (o es nulo), inspeccionar service.windows
        try {
            val windows = service.windows ?: return RootResolution(null, false)
            val candidateWindows = windows
                .filter { win ->
                    win.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                        win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD
                }
                .sortedByDescending { it.layer }

            // Priorizar la ventana cuyo paquete coincida con el evento recibido
            for (win in candidateWindows) {
                val root = win.root ?: continue
                val pkg = root.packageName?.toString()?.trim().orEmpty()
                if (pkg == OWN_PACKAGE) {
                    @Suppress("DEPRECATION")
                    try {
                        root.recycle()
                    } catch (_: Exception) {
                    }
                    continue
                }
                if (eventPkg.isNotEmpty() && pkg == eventPkg) {
                    return RootResolution(root, mustRecycleRoot = true)
                }
                @Suppress("DEPRECATION")
                try {
                    root.recycle()
                } catch (_: Exception) {
                }
            }

            // Fallback: ventana de aplicación o ventana enfocada de mayor capa que no sea nuestra app
            for (win in candidateWindows) {
                if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION && !win.isFocused) {
                    continue
                }
                val root = win.root ?: continue
                val pkg = root.packageName?.toString()?.trim().orEmpty()
                if (pkg.isNotEmpty() && pkg != OWN_PACKAGE) {
                    return RootResolution(root, mustRecycleRoot = true)
                }
                @Suppress("DEPRECATION")
                try {
                    root.recycle()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo consultar service.windows: ${e.message}")
        }

        return RootResolution(null, mustRecycleRoot = false)
    }

    private fun logDiagnosticDedup(
        packageName: String,
        category: UpdateOperationCategory,
        classification: ClassificationOutcome,
        reason: String,
        blockResult: String,
        overlayShown: Boolean
    ) {
        val key = "$packageName|${category.name}|${classification.name}|$blockResult"
        val now = System.currentTimeMillis()
        if (key == lastDiagKey && (now - lastDiagTimeMs) < DIAG_DEDUP_MS) {
            return
        }
        lastDiagKey = key
        lastDiagTimeMs = now
        eventLogger.logDiagnosticAsync(
            packageName = packageName,
            category = category,
            classification = classification,
            decisionReason = reason,
            blockResult = blockResult,
            overlayShown = overlayShown
        )
    }
}
