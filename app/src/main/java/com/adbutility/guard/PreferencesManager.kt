package com.adbutility.guard

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Utilidad pura para generar y validar hashes SHA-256 con salt para el PIN del técnico.
 */
object TechnicianPinCodec {
    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$pin".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    fun verify(pin: String, saltHex: String, expectedHash: String): Boolean {
        if (pin.isBlank() || saltHex.isBlank() || expectedHash.isBlank()) return false
        val actual = hashPin(pin, saltHex)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

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
        private const val KEY_TECH_PIN_HASH = "tech_pin_hash"
        private const val KEY_TECH_PIN_SALT = "tech_pin_salt"
        private const val KEY_TECH_MODE_UNTIL_MS = "tech_mode_until_ms"

        const val DEFAULT_COOLDOWN_SECONDS = 5
        const val DEFAULT_TECH_PAUSE_MINUTES = 5
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

    fun hasTechnicianPin(): Boolean {
        val hash = prefs.getString(KEY_TECH_PIN_HASH, "").orEmpty()
        val salt = prefs.getString(KEY_TECH_PIN_SALT, "").orEmpty()
        return hash.isNotEmpty() && salt.isNotEmpty()
    }

    fun setTechnicianPin(pin: String) {
        val cleanPin = pin.trim()
        require(cleanPin.length >= 4) { "El PIN debe tener al menos 4 dígitos." }
        val salt = TechnicianPinCodec.generateSaltHex()
        val hash = TechnicianPinCodec.hashPin(cleanPin, salt)
        prefs.edit()
            .putString(KEY_TECH_PIN_SALT, salt)
            .putString(KEY_TECH_PIN_HASH, hash)
            .apply()
    }

    fun clearTechnicianPin() {
        prefs.edit()
            .remove(KEY_TECH_PIN_SALT)
            .remove(KEY_TECH_PIN_HASH)
            .apply()
    }

    fun verifyTechnicianPin(pin: String): Boolean {
        val hash = prefs.getString(KEY_TECH_PIN_HASH, "").orEmpty()
        val salt = prefs.getString(KEY_TECH_PIN_SALT, "").orEmpty()
        return TechnicianPinCodec.verify(pin.trim(), salt, hash)
    }

    var technicianModeUntilMs: Long
        get() = prefs.getLong(KEY_TECH_MODE_UNTIL_MS, 0L)
        private set(value) = prefs.edit().putLong(KEY_TECH_MODE_UNTIL_MS, value).apply()

    fun isTechnicianModePaused(nowMs: Long = System.currentTimeMillis()): Boolean {
        val until = technicianModeUntilMs
        if (until <= 0L) return false
        if (nowMs >= until) {
            technicianModeUntilMs = 0L
            return false
        }
        return true
    }

    fun getTechnicianPauseRemainingSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val until = technicianModeUntilMs
        if (until <= nowMs) return 0L
        return ((until - nowMs) + 999L) / 1000L
    }

    fun activateTechnicianPause(durationMinutes: Int = DEFAULT_TECH_PAUSE_MINUTES) {
        val minutes = durationMinutes.coerceIn(1, 60)
        technicianModeUntilMs = System.currentTimeMillis() + minutes * 60_000L
    }

    fun endTechnicianPause() {
        technicianModeUntilMs = 0L
    }

    fun isEffectiveProtectionActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return isProtectionEnabled && !isTechnicianModePaused(nowMs)
    }
}

