package com.adbutility.guard.admin

/**
 * Estado actual e historial básico de la administración del dispositivo (Device Admin).
 */
data class DeviceAdminStatus(
    val isActive: Boolean,
    val wasPreviouslyEnabled: Boolean,
    val wasDisabledNoticePending: Boolean,
    val lastEnabledAtMs: Long,
    val lastDisabledAtMs: Long
)

