package com.adbutility.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Servicio de accesibilidad para la supervisión y detección de pantallas de restablecimiento
 * y manipulación de DNS privado.
 * Cumple con los requisitos de compatibilidad desde Android 10 (API 29) hasta Android 16.
 */
class ResetAccessibilityService : AccessibilityService() {

    private lateinit var guardManager: ResetGuardManager

    companion object {
        private const val TAG = "ResetAccessibilitySvc"
    }

    override fun onCreate() {
        super.onCreate()
        guardManager = ResetGuardManager.getInstance(this)
        guardManager.currentServiceInstance = this
        Log.i(TAG, "ResetAccessibilityService iniciado y registrado en el gestor.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        guardManager = ResetGuardManager.getInstance(this)
        guardManager.currentServiceInstance = this

        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando serviceInfo programáticamente: ${e.message}")
        }

        Log.i(TAG, "ResetAccessibilityService conectado y activo.")
        guardManager.protectionMonitor.handleAccessibilityServiceConnected()

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            if (guardManager.currentServiceInstance === this && !guardManager.overlayController.isOverlayShowing()) {
                guardManager.updateGuardEngine.evaluateCurrentWindowOnConnect(this)
            }
        }, 300L)
        handler.postDelayed({
            if (guardManager.currentServiceInstance === this &&
                !guardManager.overlayController.isOverlayShowing() &&
                !guardManager.updateGuardEngine.hasActiveProtection()
            ) {
                guardManager.updateGuardEngine.evaluateCurrentWindowOnConnect(this)
            }
        }, 1000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            return
        }

        var rootNode: AccessibilityNodeInfo? = null
        try {
            rootNode = try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            }

            // 1. Módulo Update Guard (procesa cambios de ventana, contenido e interacciones)
            if (!guardManager.overlayController.isOverlayShowing() &&
                !guardManager.overlayController.isExiting()
            ) {
                guardManager.updateGuardEngine.onAccessibilityEvent(this, event, rootNode)
            }

            // 1b. Supervisión pasiva App Protection (jamás bloquea navegación ni botones)
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                guardManager.appProtectionMonitor.onAccessibilityEvent(event, rootNode)
            }

            // 2. Supervisión existente de Restablecimiento de Fábrica y DNS Privado
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                return
            }

            if (guardManager.overlayController.isOverlayShowing() ||
                guardManager.overlayController.isExiting() ||
                guardManager.updateGuardEngine.hasActiveProtection()
            ) {
                return
            }

            val result = guardManager.resetDetector.evaluate(event, rootNode)

            if (result.isTargetScreen) {
                val warningMessage = if (result.isDnsTampering) {
                    getString(R.string.warning_body_dns)
                } else {
                    getString(R.string.warning_body_reset)
                }

                Log.w(TAG, "Detección positiva: ${result.packageName} - Regla: ${result.matchedRule}")
                guardManager.overlayController.showWarning(
                    service = this,
                    detectedPackage = result.packageName,
                    detectionType = result.matchedRule,
                    customMessage = warningMessage
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error capturado en onAccessibilityEvent: ${t.message}", t)
        } finally {
            try {
                @Suppress("DEPRECATION")
                rootNode?.recycle()
            } catch (_: Exception) {
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "ResetAccessibilityService interrumpido por el sistema.")
        guardManager.overlayController.dismissOverlay()
        guardManager.updateGuardEngine.clearProtectionOverlays()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (guardManager.currentServiceInstance === this) {
            guardManager.currentServiceInstance = null
        }
        guardManager.overlayController.dismissOverlay()
        guardManager.updateGuardEngine.clearProtectionOverlays()
        guardManager.protectionMonitor.handleAccessibilityServiceDisabled()
        Log.w(TAG, "ResetAccessibilityService desenlazado por el sistema o usuario.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (guardManager.currentServiceInstance === this) {
            guardManager.currentServiceInstance = null
        }
        guardManager.overlayController.dismissOverlay()
        guardManager.updateGuardEngine.clearProtectionOverlays()
        if (!guardManager.protectionMonitor.isAccessibilityServiceReallyActive()) {
            guardManager.protectionMonitor.handleAccessibilityServiceDisabled()
        }
        Log.i(TAG, "ResetAccessibilityService destruido.")
    }
}
