package com.adbutility.guard.ui.protection

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.adbutility.guard.R
import com.adbutility.guard.databinding.ViewProtectionStatusCardBinding
import com.adbutility.guard.protection.ProtectionLevel
import com.adbutility.guard.protection.ProtectionSnapshot

/**
 * Componente visual reutilizable para mostrar el estado actual de App Protection
 * sin exponer paquetes, clases ni detalles técnicos internos.
 */
class ProtectionStatusCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewProtectionStatusCardBinding =
        ViewProtectionStatusCardBinding.inflate(LayoutInflater.from(context), this, true)

    fun bind(snapshot: ProtectionSnapshot) {
        val colorSuccess = ContextCompat.getColor(context, R.color.brand_success)
        val colorWarning = ContextCompat.getColor(context, R.color.brand_warning)
        val colorDanger = ContextCompat.getColor(context, R.color.brand_danger)

        val (overallColor, overallText) = when (snapshot.level) {
            ProtectionLevel.FULL_PROTECTION ->
                colorSuccess to context.getString(R.string.app_protection_state_full)

            ProtectionLevel.PARTIAL_PROTECTION ->
                colorWarning to context.getString(R.string.app_protection_state_partial)

            ProtectionLevel.UNPROTECTED ->
                colorDanger to context.getString(R.string.app_protection_state_unprotected)
        }

        binding.cardContainer.strokeColor = overallColor
        ViewCompat.setBackgroundTintList(
            binding.viewStatusIndicator,
            ColorStateList.valueOf(overallColor)
        )
        binding.tvOverallStatus.text = overallText
        binding.tvOverallStatus.setTextColor(overallColor)

        // Fila 1: Estado de protección
        binding.tvRowProtectionState.text = when (snapshot.level) {
            ProtectionLevel.FULL_PROTECTION -> context.getString(R.string.app_protection_active)
            ProtectionLevel.PARTIAL_PROTECTION -> context.getString(R.string.app_protection_state_partial)
            ProtectionLevel.UNPROTECTED -> context.getString(R.string.app_protection_inactive)
        }
        binding.tvRowProtectionState.setTextColor(overallColor)

        // Fila 2: Administración del dispositivo
        if (snapshot.isDeviceAdminActive) {
            binding.tvRowDeviceAdmin.setText(R.string.app_protection_active)
            binding.tvRowDeviceAdmin.setTextColor(colorSuccess)
        } else {
            binding.tvRowDeviceAdmin.setText(R.string.app_protection_inactive)
            binding.tvRowDeviceAdmin.setTextColor(colorDanger)
        }

        // Fila 3: Servicio de protección
        if (snapshot.isAccessibilityServiceActive) {
            binding.tvRowProtectionService.setText(R.string.app_protection_service_active)
            binding.tvRowProtectionService.setTextColor(colorSuccess)
        } else {
            binding.tvRowProtectionService.setText(R.string.app_protection_service_inactive)
            binding.tvRowProtectionService.setTextColor(colorDanger)
        }

        // Fila 4: Supervisión de seguridad
        if (snapshot.isSecuritySupervisionActive) {
            binding.tvRowSecurityMonitor.setText(R.string.app_protection_supervision_active)
            binding.tvRowSecurityMonitor.setTextColor(colorSuccess)
        } else {
            binding.tvRowSecurityMonitor.setText(R.string.app_protection_supervision_standby)
            binding.tvRowSecurityMonitor.setTextColor(colorWarning)
        }
    }
}

