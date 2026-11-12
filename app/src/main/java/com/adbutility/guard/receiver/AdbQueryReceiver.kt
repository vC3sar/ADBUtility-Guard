package com.adbutility.guard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adbutility.guard.AdbCommunication
import com.adbutility.guard.ResetGuardManager

/**
 * Receptor de difusión para consultas ADB rápidas vía `am broadcast`.
 * Permite que clientes de escritorio lean el resultado directamente de stdout.
 */
class AdbQueryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_QUERY = "com.adbutility.guard.QUERY"
        const val EXTRA_ACTION = "action"
        private const val TAG = "AdbQueryReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val comm = AdbCommunication(context)
        val requestedAction = intent.getStringExtra(EXTRA_ACTION) ?: "status"

        val responseJson = when (requestedAction.lowercase()) {
            "version" -> comm.getAppVersionInfo().toString()
            "last_detection" -> comm.getLastDetection().toString()
            "history" -> comm.getDetectionHistory().toString()
            "clear" -> comm.clearHistory().toString()
            "status" -> comm.getServiceStatus().toString()
            "test" -> {
                ResetGuardManager.getInstance(context).triggerTestOverlay()
                "{\"status\":\"test_overlay_triggered\"}"
            }
            else -> comm.getServiceStatus().toString()
        }

        Log.i(TAG, "ADB_BROADCAST_REPLY: $responseJson")
        resultData = responseJson
    }
}
