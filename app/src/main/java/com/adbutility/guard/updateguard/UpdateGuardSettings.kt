package com.adbutility.guard.updateguard

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacena el estado de configuración del módulo Update Guard.
 * Mantiene una configuración mínima orientada a estabilidad sin exponer opciones técnicas.
 */
class UpdateGuardSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "adbutility_update_guard_prefs"
        private const val KEY_ENABLED = "update_guard_enabled"
    }

    var isUpdateGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
}

