package com.adbutility.guard.updateguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.adbutility.guard.R
import com.adbutility.guard.ui.updateguard.UpdateBlockedDialog

/**
 * Controla la presentación del aviso visual de ADBUtility Guard («Actualización bloqueada»).
 * Aplica deduplicación y control de frecuencia para evitar abrir avisos repetidos ante
 * ráfagas de eventos de accesibilidad del mismo diálogo.
 */
class UpdateWarningController(private val appContext: Context) {

    companion object {
        private const val TAG = "UpdateWarningCtrl"
        private const val MIN_RESHOW_INTERVAL_MS = 1800L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDialogView: View? = null
    private var activeWindowManager: WindowManager? = null
    private var isDialogVisible = false

    private var lastWarnedSignature: String = ""
    private var lastDismissedTimeMs: Long = 0L

    fun isWarningVisible(): Boolean = isDialogVisible

    /**
     * Muestra el aviso visual cuando corresponde según las reglas de deduplicación.
     *
     * @param forceFromShieldTap true si el usuario volvió a pulsar explícitamente un botón bloqueado.
     * @return true si el aviso se mostró o ya estaba visible; false si falló o se omitió por frecuencia.
     */
    @Synchronized
    fun showBlockedWarning(
        service: AccessibilityService?,
        dialogSignature: String,
        forceFromShieldTap: Boolean = false
    ): Boolean {
        if (service == null) return false

        val now = System.currentTimeMillis()
        if (isDialogVisible) {
            return true
        }

        // Control de deduplicación: si pertenece al mismo diálogo ya notificado y el usuario
        // ya presionó «Entendido», no volver a mostrar salvo que pulse el escudo protegido.
        if (!forceFromShieldTap && dialogSignature.isNotEmpty() && dialogSignature == lastWarnedSignature) {
            return false
        }

        // Control de frecuencia mínima si proviene de toques repetidos sobre el escudo
        if (forceFromShieldTap && (now - lastDismissedTimeMs) < MIN_RESHOW_INTERVAL_MS) {
            return false
        }

        return if (Looper.myLooper() == Looper.getMainLooper()) {
            showInternal(service, dialogSignature)
        } else {
            mainHandler.post {
                showInternal(service, dialogSignature)
            }
            true
        }
    }

    private fun showInternal(
        service: AccessibilityService,
        dialogSignature: String
    ): Boolean {
        if (isDialogVisible) return true

        return try {
            val themedContext = ContextThemeWrapper(service, R.style.Theme_ADBUtilityGuard)
            val wm = (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)

            val dialogView = UpdateBlockedDialog(themedContext) {
                // El botón «Entendido» únicamente cierra el aviso; jamás reinicia ni actualiza.
                lastDismissedTimeMs = System.currentTimeMillis()
                dismissWarning(resetSessionSignature = false)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                title = "ADBUtilityUpdateGuardDialog"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                }
            }

            wm.addView(dialogView, params)
            activeDialogView = dialogView
            activeWindowManager = wm
            isDialogVisible = true
            lastWarnedSignature = dialogSignature
            Log.i(TAG, "Aviso visual de actualización bloqueada mostrado con éxito.")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo mostrar UpdateBlockedDialog: ${t.message}", t)
            isDialogVisible = false
            activeDialogView = null
            false
        }
    }

    @Synchronized
    fun dismissWarning(resetSessionSignature: Boolean = false) {
        if (resetSessionSignature) {
            lastWarnedSignature = ""
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeDialogViewInternal()
        } else {
            mainHandler.post {
                synchronized(this@UpdateWarningController) {
                    removeDialogViewInternal()
                }
            }
        }
    }

    @Synchronized
    fun resetSession() {
        lastWarnedSignature = ""
        lastDismissedTimeMs = 0L
    }

    private fun removeDialogViewInternal() {
        activeDialogView?.let { view ->
            try {
                activeWindowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    activeWindowManager?.removeView(view)
                } catch (_: Exception) {
                }
            }
        }
        activeDialogView = null
        activeWindowManager = null
        isDialogVisible = false
    }
}

