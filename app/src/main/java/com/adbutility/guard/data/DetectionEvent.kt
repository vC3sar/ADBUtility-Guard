package com.adbutility.guard.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Representa un evento registrado cuando se detecta el acceso a opciones de restablecimiento.
 * Almacena únicamente metadatos de detección necesarios (sin datos personales).
 */
data class DetectionEvent(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val detectionType: String,
    val actionTaken: String
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("formattedDate", getFormattedDate())
            put("packageName", packageName)
            put("detectionType", detectionType)
            put("actionTaken", actionTaken)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): DetectionEvent {
            return DetectionEvent(
                id = json.optLong("id", 0),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                packageName = json.optString("packageName", "unknown"),
                detectionType = json.optString("detectionType", "unknown"),
                actionTaken = json.optString("actionTaken", "unknown")
            )
        }
    }
}

