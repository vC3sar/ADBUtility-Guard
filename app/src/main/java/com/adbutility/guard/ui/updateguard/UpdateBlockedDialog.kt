package com.adbutility.guard.ui.updateguard

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.adbutility.guard.databinding.ViewUpdateBlockedDialogBinding

/**
 * Vista visual del aviso de actualización bloqueada de ADBUtility Guard.
 * Reproduce el diseño oscuro translúcido con escudo azul, alerta roja y botón «Entendido».
 * El botón únicamente cierra este aviso manteniendo la protección activa sobre el botón original.
 */
class UpdateBlockedDialog(
    context: Context,
    private val onUnderstandClicked: () -> Unit
) : FrameLayout(context) {

    private val binding: ViewUpdateBlockedDialogBinding =
        ViewUpdateBlockedDialogBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        binding.btnUnderstand.setOnClickListener {
            onUnderstandClicked()
        }
    }
}

