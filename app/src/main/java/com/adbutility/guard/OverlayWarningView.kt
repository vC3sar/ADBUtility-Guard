package com.adbutility.guard

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.adbutility.guard.databinding.ViewOverlayWarningBinding

/**
 * Vista flotante de advertencia moderna, compacta y profesional.
 * Auto-adaptable dinámicamente al tema Claro y Oscuro del dispositivo.
 * Presenta un único botón de salida y protección inmediata.
 */
class OverlayWarningView(
    context: Context,
    warningMessage: String? = null,
    private val onExitClicked: () -> Unit
) : FrameLayout(context) {

    private val binding: ViewOverlayWarningBinding

    init {
        val themedInflater = LayoutInflater.from(context)
        binding = ViewOverlayWarningBinding.inflate(themedInflater, this, true)

        // Asignación de textos
        binding.tvOverlayTitle.setText(R.string.warning_title)
        if (!warningMessage.isNullOrBlank()) {
            binding.tvOverlayBody.text = warningMessage
        } else {
            binding.tvOverlayBody.setText(R.string.warning_body)
        }
        binding.btnExitSecure.setText(R.string.btn_exit_secure)

        // Colores dinámicos adaptados a Claro / Oscuro
        binding.tvOverlayTitle.setTextColor(ContextCompat.getColor(context, R.color.overlay_text_title))
        binding.tvOverlayBody.setTextColor(ContextCompat.getColor(context, R.color.overlay_text_body))
        binding.btnExitSecure.setTextColor(ContextCompat.getColor(context, R.color.overlay_btn_exit_text))

        // Acción: Salir de Ajustes, volver al HOME y proteger el dispositivo
        binding.btnExitSecure.setOnClickListener {
            onExitClicked()
        }
    }
}
