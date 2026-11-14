package com.adbutility.guard.ui.protection

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.adbutility.guard.ResetGuardManager
import com.adbutility.guard.databinding.ActivityAppProtectionBinding
import com.adbutility.guard.protection.ProtectionLevel
import com.adbutility.guard.protection.ProtectionSnapshot
import com.adbutility.guard.ui.UiInsetsHelper

/**
 * Pantalla minimalista de App Protection.
 * Muestra el estado real de protección y permite al propietario activar o restaurar
 * manualmente Device Admin y el servicio de accesibilidad mediante flujos oficiales.
 */
class AppProtectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppProtectionBinding
    private lateinit var guardManager: ResetGuardManager

    private val adminActivationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshProtectionState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshProtectionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppProtectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsetsHelper.applyEdgeToEdge(this, binding.appBarAppProtection, binding.appProtectionContent)

        guardManager = ResetGuardManager.getInstance(this)

        binding.toolbarAppProtection.setNavigationOnClickListener {
            finish()
        }

        binding.btnActivateProtection.setOnClickListener {
            handleActivateAction()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProtectionState()
    }

    private fun refreshProtectionState(): ProtectionSnapshot {
        val monitor = guardManager.protectionMonitor
        val snapshot = monitor.evaluateProtection(notifyOnRegression = false)

        binding.protectionStatusCard.bind(snapshot)

        val showWarningBanner = snapshot.level != ProtectionLevel.FULL_PROTECTION &&
            (snapshot.hasPendingDeactivationNotice ||
                monitor.repository.wasAdminEverEnabled ||
                monitor.repository.wasServiceEverEnabled)

        binding.cardWarningBanner.visibility = if (showWarningBanner) View.VISIBLE else View.GONE

        binding.btnActivateProtection.visibility =
            if (snapshot.level == ProtectionLevel.FULL_PROTECTION) View.GONE else View.VISIBLE

        return snapshot
    }

    private fun handleActivateAction() {
        val monitor = guardManager.protectionMonitor
        val snapshot = monitor.evaluateProtection(notifyOnRegression = false)

        // Solicitar permiso de notificaciones en Android 13+ de forma opcional cuando aún no está concedido
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !monitor.warningManager.areNotificationsAllowed()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        when {
            !snapshot.isDeviceAdminActive -> {
                monitor.recoveryManager.openDeviceAdminActivation(this) { intent ->
                    adminActivationLauncher.launch(intent)
                }
            }

            !snapshot.isAccessibilityServiceActive -> {
                monitor.recoveryManager.openAccessibilitySettings(this)
            }

            !snapshot.isBasicProtectionConfigured -> {
                guardManager.preferencesManager.isProtectionEnabled = true
                refreshProtectionState()
            }
        }
    }
}

