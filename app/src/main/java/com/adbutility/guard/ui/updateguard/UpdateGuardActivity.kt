package com.adbutility.guard.ui.updateguard

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adbutility.guard.R
import com.adbutility.guard.ResetGuardManager
import com.adbutility.guard.databinding.ActivityUpdateGuardBinding
import com.adbutility.guard.ui.TechnicianPinDialog
import com.adbutility.guard.ui.UiInsetsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Panel de Update Guard: control de protección contra actualizaciones del sistema
 * y visibilidad diagnóstica reciente de decisiones del motor inteligente.
 */
class UpdateGuardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateGuardBinding
    private lateinit var guardManager: ResetGuardManager
    private var suppressSwitchListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateGuardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiInsetsHelper.applyEdgeToEdge(this, binding.appBarUpdateGuard, binding.updateGuardContent)

        guardManager = ResetGuardManager.getInstance(this)

        binding.toolbarUpdateGuard.setNavigationOnClickListener {
            finish()
        }

        val updateSettings = guardManager.updateGuardEngine.settings
        setSwitchCheckedSilent(updateSettings.isUpdateGuardEnabled)
        binding.switchUpdateGuard.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (!isChecked) {
                TechnicianPinDialog.verifyOrRun(
                    context = this,
                    prefs = guardManager.preferencesManager,
                    actionTitle = "Desactivar Update Guard",
                    onVerified = {
                        updateSettings.isUpdateGuardEnabled = false
                        guardManager.updateGuardEngine.clearProtectionOverlays()
                        refreshSimpleStatus()
                    },
                    onCancelled = {
                        setSwitchCheckedSilent(true)
                    }
                )
            } else {
                updateSettings.isUpdateGuardEnabled = true
                refreshSimpleStatus()
            }
        }

        binding.btnRefreshUpdateDiag.setOnClickListener {
            refreshDiagnosticsCard()
        }

        binding.btnClearUpdateDiag.setOnClickListener {
            TechnicianPinDialog.verifyOrRun(
                context = this,
                prefs = guardManager.preferencesManager,
                actionTitle = "Limpiar diagnóstico de Update Guard",
                onVerified = {
                    guardManager.updateGuardEngine.eventLogger.clearDiagnostics()
                    refreshDiagnosticsCard()
                    Toast.makeText(this, "Diagnóstico limpiado", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSimpleStatus()
        refreshDiagnosticsCard()
    }

    private fun setSwitchCheckedSilent(checked: Boolean) {
        suppressSwitchListener = true
        binding.switchUpdateGuard.isChecked = checked
        suppressSwitchListener = false
    }

    private fun refreshSimpleStatus() {
        val isGuardEnabled = guardManager.updateGuardEngine.settings.isUpdateGuardEnabled
        val isPausedByTech = guardManager.preferencesManager.isTechnicianModePaused()
        val isServiceReady = guardManager.isAccessibilityServiceEnabled()
        val isProtecting = isGuardEnabled && isServiceReady && !isPausedByTech

        setSwitchCheckedSilent(isGuardEnabled)

        if (isGuardEnabled) {
            binding.tvUpdateProtectionState.setText(R.string.update_guard_protection_on)
        } else {
            binding.tvUpdateProtectionState.setText(R.string.update_guard_protection_off)
        }

        when {
            isPausedByTech -> {
                binding.tvUpdateDeviceState.setText(R.string.tech_mode_pause_banner)
                binding.tvUpdateDeviceState.setTextColor(ContextCompat.getColor(this, R.color.brand_warning))
            }
            isProtecting -> {
                binding.tvUpdateDeviceState.setText(R.string.update_guard_state_active)
                binding.tvUpdateDeviceState.setTextColor(ContextCompat.getColor(this, R.color.brand_success))
            }
            else -> {
                binding.tvUpdateDeviceState.setText(R.string.update_guard_state_inactive)
                binding.tvUpdateDeviceState.setTextColor(ContextCompat.getColor(this, R.color.brand_danger))
            }
        }
    }

    private fun refreshDiagnosticsCard() {
        val logger = guardManager.updateGuardEngine.eventLogger
        val blockedCount = logger.getBlockedCount()
        val records = logger.getRecentDiagnostics(12)

        binding.tvUpdateDiagSummary.text =
            "Bloqueos confirmados: $blockedCount • Últimos eventos: ${records.size}"

        if (records.isEmpty()) {
            binding.tvUpdateDiagList.setText(R.string.update_diag_empty)
            return
        }

        val sdf = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val formatted = records.joinToString("\n\n") { rec ->
            val time = sdf.format(Date(rec.timestamp))
            val tag = if (rec.classification == "CONFIRMED_SYSTEM_UPDATE") "BLOQUEADO" else rec.category
            "[$time] $tag (${rec.blockResult})\n${rec.packageName} — ${rec.decisionReason}"
        }
        binding.tvUpdateDiagList.text = formatted
    }
}
