package com.adbutility.guard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.adbutility.guard.databinding.ActivityMainBinding
import com.adbutility.guard.databinding.DialogHistoryBinding
import com.adbutility.guard.databinding.DialogSettingsBinding
import com.adbutility.guard.protection.ProtectionLevel
import com.adbutility.guard.ui.protection.AppProtectionActivity
import com.adbutility.guard.ui.updateguard.UpdateGuardActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla principal de ADBUtility Guard.
 * Presenta el estado de supervisión, configuración y métricas en interfaz Material 3.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var guardManager: ResetGuardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        guardManager = ResetGuardManager.getInstance(this)
        guardManager.protectionMonitor.schedulePeriodicChecks()

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun setupUI() {
        // Interruptor de estado de protección (Activa / Pausada)
        binding.switchProtection.isChecked = guardManager.preferencesManager.isProtectionEnabled
        binding.switchProtection.setOnCheckedChangeListener { _, isChecked ->
            guardManager.preferencesManager.isProtectionEnabled = isChecked
            updateProtectionStatusText(isChecked)
            Snackbar.make(
                binding.root,
                if (isChecked) "Protección activada" else "Protección pausada",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        // Botón para habilitar accesibilidad en Ajustes del Sistema
        binding.btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Abrir panel de Update Guard
        binding.cardUpdateGuard.setOnClickListener {
            startActivity(Intent(this, UpdateGuardActivity::class.java))
        }

        // Abrir panel de App Protection
        binding.cardAppProtection.setOnClickListener {
            startActivity(Intent(this, AppProtectionActivity::class.java))
        }

        // Ver Historial
        binding.btnViewHistory.setOnClickListener {
            showHistoryDialog()
        }

        // Configuración
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Probar Advertencia Flotante
        binding.btnTestOverlay.setOnClickListener {
            if (!guardManager.isAccessibilityServiceEnabled()) {
                Snackbar.make(
                    binding.root,
                    "Debes habilitar el servicio de accesibilidad primero para probar la ventana flotante.",
                    Snackbar.LENGTH_LONG
                ).setAction("Activar") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.show()
                return@setOnClickListener
            }

            guardManager.triggerTestOverlay { action ->
                refreshDashboard()
            }
        }
    }

    private fun refreshDashboard() {
        val isServiceActive = guardManager.isAccessibilityServiceEnabled()

        // Estado del Servicio de Accesibilidad
        if (isServiceActive) {
            binding.ivServiceStatusIcon.setImageResource(R.drawable.ic_check)
            binding.ivServiceStatusIcon.setColorFilter(
                ContextCompat.getColor(this, R.color.brand_success)
            )
            binding.tvServiceStatusTitle.setText(R.string.status_service_active)
            binding.tvServiceStatusDesc.setText(R.string.status_service_desc_active)
            binding.btnEnableService.visibility = View.GONE
        } else {
            binding.ivServiceStatusIcon.setImageResource(R.drawable.ic_warning_shield)
            binding.ivServiceStatusIcon.setColorFilter(
                ContextCompat.getColor(this, R.color.brand_danger)
            )
            binding.tvServiceStatusTitle.setText(R.string.status_service_inactive)
            binding.tvServiceStatusDesc.setText(R.string.status_service_desc_inactive)
            binding.btnEnableService.visibility = View.VISIBLE
        }

        // Estado de Protección
        val isProtectionEnabled = guardManager.preferencesManager.isProtectionEnabled
        binding.switchProtection.isChecked = isProtectionEnabled
        updateProtectionStatusText(isProtectionEnabled)

        // Estado simple de Update Guard
        val isUpdateGuardOn = guardManager.updateGuardEngine.settings.isUpdateGuardEnabled && isServiceActive
        if (isUpdateGuardOn) {
            binding.tvMainUpdateGuardStatus.setText(R.string.update_guard_state_active)
            binding.tvMainUpdateGuardStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_success))
        } else {
            binding.tvMainUpdateGuardStatus.setText(R.string.update_guard_state_inactive)
            binding.tvMainUpdateGuardStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_danger))
        }

        // Estado real de App Protection
        val appProtectionSnapshot = guardManager.protectionMonitor.evaluateProtection(notifyOnRegression = true)
        when (appProtectionSnapshot.level) {
            ProtectionLevel.FULL_PROTECTION -> {
                binding.tvMainAppProtectionStatus.setText(R.string.app_protection_state_full)
                binding.tvMainAppProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_success))
            }
            ProtectionLevel.PARTIAL_PROTECTION -> {
                binding.tvMainAppProtectionStatus.setText(R.string.app_protection_state_partial)
                binding.tvMainAppProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_warning))
            }
            ProtectionLevel.UNPROTECTED -> {
                binding.tvMainAppProtectionStatus.setText(R.string.app_protection_state_unprotected)
                binding.tvMainAppProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_danger))
            }
        }

        if (appProtectionSnapshot.hasPendingDeactivationNotice &&
            appProtectionSnapshot.level != ProtectionLevel.FULL_PROTECTION
        ) {
            Snackbar.make(
                binding.root,
                getString(R.string.app_protection_warning_message),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.app_protection_warning_action)) {
                startActivity(Intent(this, AppProtectionActivity::class.java))
            }.show()
        }

        // Contador de Detecciones
        val totalCount = guardManager.preferencesManager.totalDetectionsCount
        binding.tvDetectionsCount.text = totalCount.toString()

        // Última Detección
        val lastTime = guardManager.preferencesManager.lastDetectionTime
        if (lastTime > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date(lastTime))
            val pkg = guardManager.preferencesManager.lastDetectionPackage
            val act = guardManager.preferencesManager.lastActionTaken
            binding.tvLastDetectionDetails.text = "$dateStr\nPaquete: $pkg\nAcción: $act"
        } else {
            binding.tvLastDetectionDetails.setText(R.string.label_no_detections)
        }
    }

    private fun updateProtectionStatusText(enabled: Boolean) {
        if (enabled) {
            binding.tvProtectionDesc.setText(R.string.desc_protection_active)
            binding.tvProtectionTitle.setTextColor(
                ContextCompat.getColor(this, R.color.brand_success)
            )
        } else {
            binding.tvProtectionDesc.setText(R.string.desc_protection_paused)
            binding.tvProtectionTitle.setTextColor(
                ContextCompat.getColor(this, R.color.brand_danger)
            )
        }
    }

    private fun showHistoryDialog() {
        val dialogBinding = DialogHistoryBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        val adapter = HistoryAdapter()
        dialogBinding.rvHistory.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvHistory.adapter = adapter

        fun loadData() {
            val events = guardManager.eventLogger.getRecentEvents(100)
            if (events.isEmpty()) {
                dialogBinding.tvEmptyHistory.visibility = View.VISIBLE
                dialogBinding.rvHistory.visibility = View.GONE
            } else {
                dialogBinding.tvEmptyHistory.visibility = View.GONE
                dialogBinding.rvHistory.visibility = View.VISIBLE
                adapter.updateData(events)
            }
        }

        loadData()

        dialogBinding.btnClearHistory.setOnClickListener {
            guardManager.eventLogger.clearHistory()
            loadData()
            refreshDashboard()
        }

        dialogBinding.btnCloseHistory.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        val currentCooldown = guardManager.preferencesManager.cooldownSeconds
        dialogBinding.sliderCooldown.value = currentCooldown.coerceIn(2, 30).toFloat()
        dialogBinding.tvCooldownLabel.text = "Tiempo de enfriamiento: ${currentCooldown}s"

        dialogBinding.sliderCooldown.addOnChangeListener { _, value, _ ->
            dialogBinding.tvCooldownLabel.text = "Tiempo de enfriamiento: ${value.toInt()}s"
        }

        dialogBinding.switchVibration.isChecked = guardManager.preferencesManager.isVibrationEnabled
        dialogBinding.switchSound.isChecked = guardManager.preferencesManager.isSoundEnabled

        dialogBinding.btnCloseSettings.setOnClickListener {
            guardManager.preferencesManager.cooldownSeconds = dialogBinding.sliderCooldown.value.toInt()
            guardManager.preferencesManager.isVibrationEnabled = dialogBinding.switchVibration.isChecked
            guardManager.preferencesManager.isSoundEnabled = dialogBinding.switchSound.isChecked
            dialog.dismiss()
        }

        dialog.show()
    }
}

