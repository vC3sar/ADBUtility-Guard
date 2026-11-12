package com.adbutility.guard.updateguard

/**
 * Registro contextual de paquetes emisores.
 * IMPORTANTE: El rol del paquete NUNCA se utiliza como única prueba para bloquear o permitir
 * de forma absoluta (excepto tiendas/instaladores cuando el contexto confirma operación de app);
 * siempre se combina con el análisis del contenido y las acciones de la pantalla.
 */
object UpdatePackageRegistry {

    enum class PackageRole {
        /** Actualizadores dedicados de firmware / OTA de fabricantes o AOSP */
        DEDICATED_OTA_UPDATER,
        /** Paquetes duales capaces de presentar avisos de sistema OTA o diálogos normales (p. ej. GMS, Ajustes, SystemUI) */
        DUAL_PURPOSE_SYSTEM_OR_GMS,
        /** Tiendas oficiales de aplicaciones (Play Store, Palm Store, Galaxy Store, etc.) */
        APP_STORE,
        /** Instaladores de paquetes APK / gestores de instalación */
        PACKAGE_INSTALLER,
        /** Cualquier otra aplicación de usuario o del sistema */
        OTHER
    }

    private val DEDICATED_OTA_PACKAGES = setOf(
        // TECNO / Transsion / Infinix / Itel (HiOS / XOS)
        "com.transsion.systemupdate",
        "com.transsion.fotaclient",
        "com.transsion.osupdate",
        "com.transsion.ota",
        "com.itel.systemupdate",
        "com.infinix.systemupdate",
        // AOSP / Google / MediaTek / Spreadtrum / FOTA genéricos
        "com.android.dynsystem",
        "com.google.android.factoryota",
        "com.adups.fota",
        "com.redstone.ota.ui",
        "com.mediatek.systemupdate",
        "com.mediatek.fota",
        "com.sprd.systemupdate",
        // Samsung FOTA
        "com.wssyncmldm",
        "com.sec.android.soagent",
        "com.samsung.android.fota",
        // Xiaomi / HyperOS / MIUI
        "com.android.updater",
        // Motorola OTA
        "com.motorola.ccc.ota",
        "com.motorola.ccc.mainplumbing",
        // OPPO / Realme / OnePlus OTA
        "com.oplus.ota",
        "com.coloros.ota",
        "com.oneplus.opbackup"
    )

    private val DUAL_PURPOSE_SYSTEM_PACKAGES = setOf(
        "com.google.android.gms",
        "com.android.settings",
        "com.google.android.settings",
        "com.transsion.settings",
        "com.samsung.android.settings",
        "com.miui.settings",
        "com.oplus.settings",
        "com.coloros.settings",
        "com.motorola.settings",
        "com.android.systemui",
        "android"
    )

    private val APP_STORE_PACKAGES = setOf(
        "com.android.vending",
        "com.transsion.palmstore",
        "com.transsion.appstore",
        "com.sec.android.app.samsungapps",
        "com.xiaomi.mipicks",
        "com.xiaomi.market",
        "com.heytap.market",
        "com.oppo.market",
        "com.huawei.appmarket",
        "com.amazon.venezia",
        "org.fdroid.fdroid",
        "com.aurora.store"
    )

    private val PACKAGE_INSTALLER_PACKAGES = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.oplus.appinstaller",
        "com.transsion.packageinstaller",
        "com.android.shell"
    )

    fun classifyPackage(packageName: String): PackageRole {
        if (packageName.isBlank()) return PackageRole.OTHER
        val pkg = packageName.trim().lowercase()

        return when {
            pkg in DEDICATED_OTA_PACKAGES -> PackageRole.DEDICATED_OTA_UPDATER
            pkg in DUAL_PURPOSE_SYSTEM_PACKAGES -> PackageRole.DUAL_PURPOSE_SYSTEM_OR_GMS
            pkg in APP_STORE_PACKAGES -> PackageRole.APP_STORE
            pkg in PACKAGE_INSTALLER_PACKAGES -> PackageRole.PACKAGE_INSTALLER
            pkg.contains("fota") || pkg.endsWith(".systemupdate") || pkg.endsWith(".ota") ->
                PackageRole.DEDICATED_OTA_UPDATER
            pkg.contains("packageinstaller") || pkg.contains("appinstaller") ->
                PackageRole.PACKAGE_INSTALLER
            else -> PackageRole.OTHER
        }
    }
}

