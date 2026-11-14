package com.adbutility.guard.ui

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.LinearLayout
import android.widget.Toast
import com.adbutility.guard.PreferencesManager
import com.adbutility.guard.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Diálogos Material 3 compartidos para validar y gestionar el PIN del técnico y la pausa temporal.
 */
object TechnicianPinDialog {

    fun verifyOrRun(
        context: Context,
        prefs: PreferencesManager,
        actionTitle: String,
        onVerified: () -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        if (!prefs.hasTechnicianPin() || prefs.isTechnicianModePaused()) {
            onVerified()
            return
        }

        val inputLayout = buildPinInputLayout(context, context.getString(R.string.tech_mode_pin_hint))
        val editText = inputLayout.editText!!

        val container = wrapInput(context, inputLayout)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(actionTitle)
            .setMessage("Ingresa el PIN del técnico para continuar.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Verificar", null)
            .setNegativeButton(R.string.btn_close) { d, _ ->
                d.dismiss()
                onCancelled()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = editText.text?.toString()?.trim().orEmpty()
                if (prefs.verifyTechnicianPin(entered)) {
                    dialog.dismiss()
                    onVerified()
                } else {
                    inputLayout.error = "PIN incorrecto"
                }
            }
        }
        dialog.show()
    }

    fun showManageDialog(
        context: Context,
        prefs: PreferencesManager,
        onStateChanged: (pausedNow: Boolean) -> Unit
    ) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (prefs.isTechnicianModePaused()) {
            options.add("Reanudar protecciones ahora")
            actions.add {
                prefs.endTechnicianPause()
                onStateChanged(false)
                Toast.makeText(context, "Protecciones reanudadas", Toast.LENGTH_SHORT).show()
            }
        } else {
            options.add("Pausar protecciones 5 min (Modo Técnico)")
            actions.add {
                prefs.activateTechnicianPause(PreferencesManager.DEFAULT_TECH_PAUSE_MINUTES)
                onStateChanged(true)
                Toast.makeText(context, "Modo técnico activo por 5 minutos", Toast.LENGTH_SHORT).show()
            }
        }

        if (prefs.hasTechnicianPin()) {
            options.add("Cambiar PIN del técnico")
            actions.add { showSetPinDialog(context, prefs) { onStateChanged(prefs.isTechnicianModePaused()) } }
            options.add("Eliminar PIN del técnico")
            actions.add {
                prefs.clearTechnicianPin()
                onStateChanged(prefs.isTechnicianModePaused())
                Toast.makeText(context, "PIN eliminado", Toast.LENGTH_SHORT).show()
            }
        } else {
            options.add("Configurar PIN del técnico (4+ dígitos)")
            actions.add { showSetPinDialog(context, prefs) { onStateChanged(prefs.isTechnicianModePaused()) } }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tech_mode_title)
            .setItems(options.toTypedArray()) { _, index ->
                actions[index].invoke()
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun showSetPinDialog(
        context: Context,
        prefs: PreferencesManager,
        onSaved: () -> Unit
    ) {
        val inputLayout = buildPinInputLayout(context, "Nuevo PIN (4 a 8 dígitos)")
        val editText = inputLayout.editText!!
        val container = wrapInput(context, inputLayout)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (prefs.hasTechnicianPin()) "Cambiar PIN" else "Configurar PIN")
            .setMessage("Se solicitará al desactivar protecciones, abrir Configuración o limpiar historiales.")
            .setView(container)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_close, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = editText.text?.toString()?.trim().orEmpty()
                if (pin.length < 4) {
                    inputLayout.error = "Usa al menos 4 dígitos"
                } else {
                    prefs.setTechnicianPin(pin)
                    dialog.dismiss()
                    onSaved()
                    Toast.makeText(context, "PIN del técnico guardado", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun buildPinInputLayout(context: Context, hintText: String): TextInputLayout {
        val layout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = hintText
        }
        val editText = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
        }
        layout.addView(editText)
        return layout
    }

    private fun wrapInput(context: Context, inputLayout: TextInputLayout): LinearLayout {
        val dp16 = (16 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16 / 2, dp16, 0)
            addView(inputLayout)
        }
    }
}

