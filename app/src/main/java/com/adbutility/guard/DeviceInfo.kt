package com.adbutility.guard

import android.os.Build
import org.json.JSONObject

/**
 * Provee información detallada sobre el dispositivo y el sistema operativo,
 * identificando capas de personalización (One UI, HyperOS, etc.) para el motor de detección.
 */
object DeviceInfo {

    enum class OemType {
        SAMSUNG,
        XIAOMI,
        PIXEL,
        MOTOROLA,
        AOSP_GENERIC
    }

    val manufacturer: String = Build.MANUFACTURER
    val brand: String = Build.BRAND
    val model: String = Build.MODEL
    val device: String = Build.DEVICE
    val sdkInt: Int = Build.VERSION.SDK_INT
    val release: String = Build.VERSION.RELEASE
    val securityPatch: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Build.VERSION.SECURITY_PATCH
    } else {
        "Unknown"
    }

    val oemType: OemType by lazy {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        when {
            m.contains("samsung") || b.contains("samsung") -> OemType.SAMSUNG
            m.contains("xiaomi") || b.contains("xiaomi") || m.contains("redmi") || b.contains("poco") -> OemType.XIAOMI
            m.contains("google") || b.contains("google") -> OemType.PIXEL
            m.contains("motorola") || b.contains("moto") -> OemType.MOTOROLA
            else -> OemType.AOSP_GENERIC
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("manufacturer", manufacturer)
            put("brand", brand)
            put("model", model)
            put("device", device)
            put("androidVersion", release)
            put("sdkInt", sdkInt)
            put("securityPatch", securityPatch)
            put("oemType", oemType.name)
        }
    }
}

