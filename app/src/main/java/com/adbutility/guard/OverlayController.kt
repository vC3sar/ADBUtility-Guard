package com.adbutility.guard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Controlador de la ventana flotante de advertencia.
 * Proyecta la alerta con botón único de salida y protección.
 * Auto-adaptable al modo Claro / Oscuro del sistema.
 */
class OverlayController(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val eventLogger: EventLogger
) {

    companion object {
        private const val TAG = "OverlayController"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeOverlayView: View? = null
    private var activeWindowManager: WindowManager? = null
    private var isShowing = false
    private var exitSuppressionUntil: Long = 0L

    fun isExiting(): Boolean = System.currentTimeMillis() < exitSuppressionUntil

    @Synchronized
    fun showWarning(
        service: AccessibilityService?,
        detectedPackage: String,
        detectionType: String,
        customMessage: String? = null,
        onActionResolved: ((String) -> Unit)? = null
    ) {
        mainHandler.post {
            if (isShowing || isExiting()) {
                Log.d(TAG, "La advertencia ya está visible o en proceso de salida segura. Se descarta solicitud.")
                return@post
            }

            try {
                val serviceContext = service ?: context

                // Crear contexto con tema Material 3 respetando la configuración dinámica (Día/Noche)
                val sysConfig = Configuration(serviceContext.resources.configuration)
                val themedContext = ContextThemeWrapper(serviceContext, R.style.Theme_ADBUtilityGuard).apply {
                    applyOverrideConfiguration(sysConfig)
                }

                val wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)

                activeWindowManager = wm

                // Feedback háptico de alerta
                triggerAlertFeedback()

                val overlayView = OverlayWarningView(
                    context = themedContext,
                    warningMessage = customMessage,
                    onExitClicked = {
                        // 1. Activar periodo de supresión de salida segura (2.5 segundos)
                        exitSuppressionUntil = System.currentTimeMillis() + 2500L

                        // 2. Descartar la vista de advertencia flotante
                        dismissOverlay()

                        // 3. Ejecutar acción global BACK para descartar diálogos abiertos (p. ej. diálogo modal de DNS)
                        try {
                            service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error ejecutando GLOBAL_ACTION_BACK: ${e.message}")
                        }

                        // 4. Resetear la tarea de Ajustes a su pantalla principal raíz (SettingsHomepageActivity).
                        // FLAG_ACTIVITY_CLEAR_TASK y FLAG_ACTIVITY_CLEAR_TOP limpian el backstack de Ajustes
                        // para que la próxima vez que el usuario abra Ajustes desde el launcher/recientes,
                        // inicie limpiamente en la lista principal y NO quede atrapado en el bucle.
                        try {
                            val resetSettingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            serviceContext.startActivity(resetSettingsIntent)
                            Log.i(TAG, "Tarea de Ajustes reseteada a la raíz principal con éxito.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reseteando tarea de Ajustes: ${e.message}")
                        }

                        // 5. Inmediatamente después de resetear la pila, enviar al usuario al HOME del sistema
                        mainHandler.postDelayed({
                            try {
                                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                serviceContext.startActivity(homeIntent)
                                Log.i(TAG, "Acción: SALIR Y PROTEGER ejecutada con éxito. Navegado a HOME.")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error retornando a HOME: ${e.message}")
                            }
                        }, 120L)

                        eventLogger.logDetection(detectedPackage, detectionType, "SALIR Y PROTEGER")
                        onActionResolved?.invoke("SALIR Y PROTEGER")
                    }
                )

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    title = "ADBUtilityGuardWarning"
                }

                wm.addView(overlayView, params)
                activeOverlayView = overlayView
                isShowing = true
                Log.i(TAG, "Advertencia proyectada con éxito para paquete: $detectedPackage")

            } catch (e: Throwable) {
                Log.e(TAG, "Excepción controlada al proyectar la advertencia: ${e.message}", e)
                isShowing = false
                activeOverlayView = null
            }
        }
    }

    @Synchronized
    fun dismissOverlay() {
        mainHandler.post {
            activeOverlayView?.let { view ->
                try {
                    activeWindowManager?.removeView(view)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al remover overlay: ${e.message}")
                }
            }
            activeOverlayView = null
            isShowing = false
        }
    }

    fun isOverlayShowing(): Boolean = isShowing

    private fun triggerAlertFeedback() {
        if (!preferencesManager.isVibrationEnabled) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.let { vib ->
                    val effect = VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(250)
            }
        } catch (_: Exception) {
        }
    }
}
