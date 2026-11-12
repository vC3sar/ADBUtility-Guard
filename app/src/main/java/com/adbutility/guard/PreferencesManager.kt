package com.adbutility.guard

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor centralizado de preferencias locales y estados de configuración del usuario.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "adbutility_guard_prefs"

        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_COOLDOWN_SECONDS = "cooldown_seconds"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_TOTAL_DETECTIONS = "total_detections"
        private const val KEY_LAST_DETECTION_TIME = "last_detection_time"
        private const val KEY_LAST_PACKAGE = "last_package"
        private const val KEY_LAST_TYPE = "last_type"
        private const val KEY_LAST_ACTION = "last_action"

        const val DEFAULT_COOLDOWN_SECONDS = 5
    }

    var isProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    var cooldownSeconds: Int
        get() = prefs.getInt(KEY_COOLDOWN_SECONDS, DEFAULT_COOLDOWN_SECONDS)
        set(value) = prefs.edit().putInt(KEY_COOLDOWN_SECONDS, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var totalDetectionsCount: Long
        get() = prefs.getLong(KEY_TOTAL_DETECTIONS, 0L)
        set(value) = prefs.edit().putLong(KEY_TOTAL_DETECTIONS, value).apply()

    var lastDetectionTime: Long
        get() = prefs.getLong(KEY_LAST_DETECTION_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DETECTION_TIME, value).apply()

    var lastDetectionPackage: String
        get() = prefs.getString(KEY_LAST_PACKAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PACKAGE, value).apply()

    var lastDetectionType: String
        get() = prefs.getString(KEY_LAST_TYPE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_TYPE, value).apply()

    var lastActionTaken: String
        get() = prefs.getString(KEY_LAST_ACTION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_ACTION, value).apply()

    fun recordDetection(pkg: String, type: String, action: String) {
        val now = System.currentTimeMillis()
        val count = totalDetectionsCount + 1
        prefs.edit()
            .putLong(KEY_TOTAL_DETECTIONS, count)
            .putLong(KEY_LAST_DETECTION_TIME, now)
            .putString(KEY_LAST_PACKAGE, pkg)
            .putString(KEY_LAST_TYPE, type)
            .putString(KEY_LAST_ACTION, action)
            .apply()
    }

    fun updateLastAction(action: String) {
        lastActionTaken = action
    }

    fun resetCounters() {
        prefs.edit()
            .putLong(KEY_TOTAL_DETECTIONS, 0L)
            .putLong(KEY_LAST_DETECTION_TIME, 0L)
            .putString(KEY_LAST_PACKAGE, "")
            .putString(KEY_LAST_TYPE, "")
            .putString(KEY_LAST_ACTION, "")
            .apply()
    }
}

