package com.adbutility.guard.ui.updateguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adbutility.guard.R
import com.adbutility.guard.ResetGuardManager
import com.adbutility.guard.databinding.ActivityUpdateGuardBinding

/**
 * Interfaz principal simplificada de Update Guard.
 * Muestra únicamente el estado de protección sin exponer detalles técnicos ni listados internos.
 */
class UpdateGuardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateGuardBinding
    private lateinit var guardManager: ResetGuardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateGuardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        guardManager = ResetGuardManager.getInstance(this)

        binding.toolbarUpdateGuard.setNavigationOnClickListener {
            finish()
        }

        val updateSettings = guardManager.updateGuardEngine.settings
        binding.switchUpdateGuard.isChecked = updateSettings.isUpdateGuardEnabled
        binding.switchUpdateGuard.setOnCheckedChangeListener { _, isChecked ->
            updateSettings.isUpdateGuardEnabled = isChecked
            if (!isChecked) {
                guardManager.updateGuardEngine.clearProtectionOverlays()
            }
            refreshSimpleStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSimpleStatus()
    }

    private fun refreshSimpleStatus() {
        val isGuardEnabled = guardManager.updateGuardEngine.settings.isUpdateGuardEnabled
        val isServiceReady = guardManager.isAccessibilityServiceEnabled()
        val isProtecting = isGuardEnabled && isServiceReady

        if (isGuardEnabled) {
            binding.tvUpdateProtectionState.setText(R.string.update_guard_protection_on)
        } else {
            binding.tvUpdateProtectionState.setText(R.string.update_guard_protection_off)
        }

        if (isProtecting) {
            binding.tvUpdateDeviceState.setText(R.string.update_guard_state_active)
            binding.tvUpdateDeviceState.setTextColor(ContextCompat.getColor(this, R.color.brand_success))
        } else {
            binding.tvUpdateDeviceState.setText(R.string.update_guard_state_inactive)
            binding.tvUpdateDeviceState.setTextColor(ContextCompat.getColor(this, R.color.brand_danger))
        }
    }
}

