package com.adbutility.guard.protection

import android.content.Context
import android.content.SharedPreferences

/**
 * Repositorio interno para historial básico de cambios de estado, deduplicación de avisos
 * y marcas de recuperación en App Protection.
 * Nota: Las comprobaciones reales del sistema siempre prevalecen sobre este repositorio.
 */
class ProtectionRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "adbutility_app_protection_prefs"

        private const val KEY_ADMIN_EVER_ENABLED = "admin_ever_enabled"
        private const val KEY_LAST_KNOWN_ADMIN_ACTIVE = "last_known_admin_active"
        private const val KEY_LAST_ADMIN_ENABLED_AT = "last_admin_enabled_at"
        private const val KEY_LAST_ADMIN_DISABLED_AT = "last_admin_disabled_at"
        private const val KEY_ADMIN_DISABLED_NOTICE_PENDING = "admin_disabled_notice_pending"

        private const val KEY_SERVICE_EVER_ENABLED = "service_ever_enabled"
        private const val KEY_LAST_KNOWN_SERVICE_ACTIVE = "last_known_service_active"
        private const val KEY_SERVICE_DISABLED_NOTICE_PENDING = "service_disabled_notice_pending"

        private const val KEY_LAST_NOTIFIED_SIGNATURE = "last_notified_signature"
        private const val KEY_LAST_NOTIFIED_AT_MS = "last_notified_at_ms"

        private const val KEY_LAST_SECURITY_SCREEN_TYPE = "last_security_screen_type"
        private const val KEY_LAST_SECURITY_SCREEN_AT_MS = "last_security_screen_at_ms"
        private const val KEY_LAST_EVALUATION_AT_MS = "last_evaluation_at_ms"
    }

    var wasAdminEverEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_EVER_ENABLED, false)
        private set(value) = prefs.edit().putBoolean(KEY_ADMIN_EVER_ENABLED, value).apply()

    var lastKnownAdminActive: Boolean
        get() = prefs.getBoolean(KEY_LAST_KNOWN_ADMIN_ACTIVE, false)
        private set(value) = prefs.edit().putBoolean(KEY_LAST_KNOWN_ADMIN_ACTIVE, value).apply()

    var lastAdminEnabledAtMs: Long
        get() = prefs.getLong(KEY_LAST_ADMIN_ENABLED_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_ADMIN_ENABLED_AT, value).apply()

    var lastAdminDisabledAtMs: Long
        get() = prefs.getLong(KEY_LAST_ADMIN_DISABLED_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_LAST_ADMIN_DISABLED_AT, value).apply()

    var isAdminDisabledNoticePending: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_DISABLED_NOTICE_PENDING, false)
        private set(value) = prefs.edit().putBoolean(KEY_ADMIN_DISABLED_NOTICE_PENDING, value).apply()

    var wasServiceEverEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_EVER_ENABLED, false)
        private set(value) = prefs.edit().putBoolean(KEY_SERVICE_EVER_ENABLED, value).apply()

    var lastKnownServiceActive: Boolean
        get() = prefs.getBoolean(KEY_LAST_KNOWN_SERVICE_ACTIVE, false)
        private set(value) = prefs.edit().putBoolean(KEY_LAST_KNOWN_SERVICE_ACTIVE, value).apply()

    var isServiceDisabledNoticePending: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_DISABLED_NOTICE_PENDING, false)
        private set(value) = prefs.edit().putBoolean(KEY_SERVICE_DISABLED_NOTICE_PENDING, value).apply()

    var lastNotifiedSignature: String
        get() = prefs.getString(KEY_LAST_NOTIFIED_SIGNATURE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_NOTIFIED_SIGNATURE, value).apply()

    var lastNotifiedAtMs: Long
        get() = prefs.getLong(KEY_LAST_NOTIFIED_AT_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NOTIFIED_AT_MS, value).apply()

    var lastEvaluationAtMs: Long
        get() = prefs.getLong(KEY_LAST_EVALUATION_AT_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_EVALUATION_AT_MS, value).apply()

    fun recordAdminEnabled() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_ADMIN_EVER_ENABLED, true)
            .putBoolean(KEY_LAST_KNOWN_ADMIN_ACTIVE, true)
            .putLong(KEY_LAST_ADMIN_ENABLED_AT, now)
            .putBoolean(KEY_ADMIN_DISABLED_NOTICE_PENDING, false)
            .apply()
    }

    fun recordAdminDisabled() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_LAST_KNOWN_ADMIN_ACTIVE, false)
            .putLong(KEY_LAST_ADMIN_DISABLED_AT, now)
            .putBoolean(KEY_ADMIN_DISABLED_NOTICE_PENDING, true)
            .apply()
    }

    fun recordServiceEnabled() {
        prefs.edit()
            .putBoolean(KEY_SERVICE_EVER_ENABLED, true)
            .putBoolean(KEY_LAST_KNOWN_SERVICE_ACTIVE, true)
            .putBoolean(KEY_SERVICE_DISABLED_NOTICE_PENDING, false)
            .apply()
    }

    fun recordServiceDisabled() {
        prefs.edit()
            .putBoolean(KEY_LAST_KNOWN_SERVICE_ACTIVE, false)
            .putBoolean(KEY_SERVICE_DISABLED_NOTICE_PENDING, true)
            .apply()
    }

    fun clearPendingNotices() {
        prefs.edit()
            .putBoolean(KEY_ADMIN_DISABLED_NOTICE_PENDING, false)
            .putBoolean(KEY_SERVICE_DISABLED_NOTICE_PENDING, false)
            .apply()
    }

    fun clearNotificationDeduplication() {
        prefs.edit()
            .putString(KEY_LAST_NOTIFIED_SIGNATURE, "")
            .putLong(KEY_LAST_NOTIFIED_AT_MS, 0L)
            .apply()
    }

    fun recordSecurityNavigationObservation(screenType: String) {
        prefs.edit()
            .putString(KEY_LAST_SECURITY_SCREEN_TYPE, screenType)
            .putLong(KEY_LAST_SECURITY_SCREEN_AT_MS, System.currentTimeMillis())
            .apply()
    }
}

