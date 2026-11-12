package com.adbutility.guard.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.adbutility.guard.AdbCommunication
import com.adbutility.guard.ResetGuardManager

/**
 * ContentProvider optimizado para integración con ADB UtilityTools mediante ADB shell.
 * Permite consultas rápidas vía 'adb shell content call' o 'adb shell content query'.
 * Seguro y estrictamente de solo lectura.
 */
class GuardContentProvider : ContentProvider() {

    private lateinit var adbComm: AdbCommunication

    companion object {
        const val AUTHORITY = "com.adbutility.guard.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        private const val TAG = "GuardContentProvider"

        // Métodos de consulta soportados
        const val METHOD_PING = "ping"
        const val METHOD_VERSION = "getVersion"
        const val METHOD_STATUS = "getStatus"
        const val METHOD_LAST_DETECTION = "getLastDetection"
        const val METHOD_HISTORY = "getHistory"
        const val METHOD_CLEAR_HISTORY = "clearHistory"
        const val METHOD_TEST = "testOverlay"

        const val KEY_RESPONSE_JSON = "response_json"
    }

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        adbComm = AdbCommunication(ctx)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val resultBundle = Bundle()
        val responseJson: String

        Log.d(TAG, "Llamada recibida vía ADB content call: method=$method, arg=$arg")

        responseJson = when (method) {
            METHOD_PING, METHOD_VERSION -> {
                adbComm.getAppVersionInfo().toString()
            }
            METHOD_STATUS -> {
                adbComm.getServiceStatus().toString()
            }
            METHOD_LAST_DETECTION -> {
                adbComm.getLastDetection().toString()
            }
            METHOD_HISTORY -> {
                val limit = arg?.toIntOrNull() ?: 50
                adbComm.getDetectionHistory(limit).toString()
            }
            METHOD_CLEAR_HISTORY -> {
                adbComm.clearHistory().toString()
            }
            METHOD_TEST -> {
                context?.let { ResetGuardManager.getInstance(it).triggerTestOverlay() }
                "{\"status\":\"test_overlay_triggered\"}"
            }
            else -> {
                "{\"error\":\"Unknown or unauthorized method: $method\"}"
            }
        }

        resultBundle.putString(KEY_RESPONSE_JSON, responseJson)
        Log.i(TAG, "ADB_GUARD_RESPONSE: $responseJson")
        return resultBundle
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val path = uri.path?.removePrefix("/") ?: ""
        val cursor = MatrixCursor(arrayOf("key", "json_data"))

        val jsonData = when (path) {
            "version" -> adbComm.getAppVersionInfo().toString()
            "status" -> adbComm.getServiceStatus().toString()
            "last_detection" -> adbComm.getLastDetection().toString()
            "history" -> adbComm.getDetectionHistory().toString()
            else -> adbComm.getServiceStatus().toString()
        }

        cursor.addRow(arrayOf(path, jsonData))
        return cursor
    }

    override fun getType(uri: Uri): String {
        return "application/json"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
