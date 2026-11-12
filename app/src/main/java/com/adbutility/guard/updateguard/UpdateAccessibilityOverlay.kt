package com.adbutility.guard.updateguard

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.adbutility.guard.R

/**
 * Gestiona escudos táctiles TYPE_ACCESSIBILITY_OVERLAY posicionados exclusivamente sobre
 * los límites exactos de los botones peligrosos identificados en un diálogo de actualización del sistema.
 *
 * Nunca cubre indiscriminadamente toda la pantalla cuando no se identifican botones válidos.
 */
class UpdateAccessibilityOverlay(private val appContext: Context) {

    companion object {
        private const val TAG = "UpdateAccessOverlay"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeShields = mutableListOf<ShieldEntry>()
    private var currentBoundsSignature: List<NodeBounds> = emptyList()

    private data class ShieldEntry(
        val view: View,
        val windowManager: WindowManager,
        val bounds: NodeBounds
    )

    fun isShieldActive(): Boolean = activeShields.isNotEmpty()

    /**
     * Aplica o actualiza escudos bloqueadores sobre los botones peligrosos especificados.
     *
     * @return true si al menos un escudo pudo adjuntarse correctamente en WindowManager;
     *         false si la ventana no pudo protegerse (p. ej. restricción del sistema/OEM o sin límites válidos).
     */
    @Synchronized
    fun applyButtonShields(
        service: AccessibilityService?,
        targets: List<ActionButtonCandidate>,
        onShieldTapped: () -> Unit
    ): Boolean {
        val validBounds = targets.map { it.boundsInScreen }.filter { it.isValid() }.distinct()
        if (service == null || validBounds.isEmpty()) {
            clearShields()
            return false
        }

        // Evitar recrear escudos idénticos en cada evento consecutivo
        if (activeShields.isNotEmpty() && currentBoundsSignature == validBounds) {
            return true
        }

        return if (Looper.myLooper() == Looper.getMainLooper()) {
            applyShieldsInternal(service, validBounds, onShieldTapped)
        } else {
            // Si excepcionalmente se invoca fuera del hilo principal, limpiar y despachar en main
            mainHandler.post {
                applyShieldsInternal(service, validBounds, onShieldTapped)
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applyShieldsInternal(
        service: AccessibilityService,
        validBounds: List<NodeBounds>,
        onShieldTapped: () -> Unit
    ): Boolean {
        removeAllViewsInternal()

        val wm = (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?: return false

        var addedCount = 0
        for ((index, bounds) in validBounds.withIndex()) {
            try {
                val shieldView = View(service).apply {
                    setBackgroundResource(R.drawable.bg_update_shield_overlay)
                    isClickable = true
                    isFocusable = false
                    contentDescription = "Protección Update Guard"
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            onShieldTapped()
                        }
                        // Consumir el evento táctil siempre para impedir que alcance el botón original
                        true
                    }
                }

                val params = WindowManager.LayoutParams(
                    bounds.width,
                    bounds.height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = bounds.left
                    y = bounds.top
                    title = "UpdateGuardShield_$index"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        fitInsetsTypes = 0
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                wm.addView(shieldView, params)
                activeShields.add(ShieldEntry(shieldView, wm, bounds))
                addedCount++
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "No se pudo adjuntar overlay sobre botón en $bounds (posible restricción OEM/Android): ${t.message}"
                )
            }
        }

        if (addedCount == 0) {
            removeAllViewsInternal()
            currentBoundsSignature = emptyList()
            return false
        }

        currentBoundsSignature = validBounds
        Log.i(TAG, "Escudos protectores aplicados sobre $addedCount botón(es) peligroso(s).")
        return true
    }

    @Synchronized
    fun clearShields() {
        currentBoundsSignature = emptyList()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeAllViewsInternal()
        } else {
            mainHandler.post {
                synchronized(this@UpdateAccessibilityOverlay) {
                    removeAllViewsInternal()
                }
            }
        }
    }

    private fun removeAllViewsInternal() {
        if (activeShields.isEmpty()) return
        val iterator = activeShields.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                entry.windowManager.removeViewImmediate(entry.view)
            } catch (_: Exception) {
                try {
                    entry.windowManager.removeView(entry.view)
                } catch (_: Exception) {
                }
            }
            iterator.remove()
        }
        Log.d(TAG, "Escudos protectores retirados y recursos liberados.")
    }
}

