package com.adbutility.guard.updateguard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registro diagnóstico interno del módulo Update Guard.
 * Guarda exclusivamente metadatos técnicos necesarios para depurar falsos positivos
 * y verificar si el overlay pudo aplicarse, sin almacenar datos personales ni textos completos.
 */
data class UpdateDiagnosticRecord(
    val id: Long = 0L,
    val timestamp: Long,
    val packageName: String,
    val category: String,
    val classification: String,
    val decisionReason: String,
    val blockResult: String,
    val overlayShown: Boolean
)

class UpdateEventLogger(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "UpdateEventLogger"
        private const val DATABASE_NAME = "adbutility_update_guard_diag.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_DIAG = "update_diagnostics"
        private const val COL_ID = "id"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_PACKAGE = "package_name"
        private const val COL_CATEGORY = "category"
        private const val COL_CLASSIFICATION = "classification"
        private const val COL_REASON = "decision_reason"
        private const val COL_BLOCK_RESULT = "block_result"
        private const val COL_OVERLAY_SHOWN = "overlay_shown"

        private const val MAX_RECORDS = 200
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DIAG (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_PACKAGE TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_CLASSIFICATION TEXT NOT NULL,
                $COL_REASON TEXT NOT NULL,
                $COL_BLOCK_RESULT TEXT NOT NULL,
                $COL_OVERLAY_SHOWN INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_update_diag_time ON $TABLE_DIAG ($COL_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DIAG")
        onCreate(db)
    }

    fun logDiagnosticAsync(
        packageName: String,
        category: UpdateOperationCategory,
        classification: ClassificationOutcome,
        decisionReason: String,
        blockResult: String,
        overlayShown: Boolean
    ) {
        val timestamp = System.currentTimeMillis()
        val safeReason = decisionReason.take(220)
        scope.launch {
            insertRecord(
                UpdateDiagnosticRecord(
                    timestamp = timestamp,
                    packageName = packageName.take(120),
                    category = category.name,
                    classification = classification.name,
                    decisionReason = safeReason,
                    blockResult = blockResult,
                    overlayShown = overlayShown
                )
            )
        }
    }

    @Synchronized
    fun insertRecord(record: UpdateDiagnosticRecord): Long {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_TIMESTAMP, record.timestamp)
                put(COL_PACKAGE, record.packageName)
                put(COL_CATEGORY, record.category)
                put(COL_CLASSIFICATION, record.classification)
                put(COL_REASON, record.decisionReason)
                put(COL_BLOCK_RESULT, record.blockResult)
                put(COL_OVERLAY_SHOWN, if (record.overlayShown) 1 else 0)
            }
            val id = db.insert(TABLE_DIAG, null, values)
            pruneOld(db)
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando diagnóstico interno: ${e.message}")
            -1L
        }
    }

    @Synchronized
    fun getRecentDiagnostics(limit: Int = 50): List<UpdateDiagnosticRecord> {
        val list = mutableListOf<UpdateDiagnosticRecord>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_DIAG,
                null,
                null,
                null,
                null,
                null,
                "$COL_TIMESTAMP DESC",
                limit.toString()
            )
            cursor.use {
                val idIdx = it.getColumnIndexOrThrow(COL_ID)
                val tsIdx = it.getColumnIndexOrThrow(COL_TIMESTAMP)
                val pkgIdx = it.getColumnIndexOrThrow(COL_PACKAGE)
                val catIdx = it.getColumnIndexOrThrow(COL_CATEGORY)
                val clsIdx = it.getColumnIndexOrThrow(COL_CLASSIFICATION)
                val rsnIdx = it.getColumnIndexOrThrow(COL_REASON)
                val resIdx = it.getColumnIndexOrThrow(COL_BLOCK_RESULT)
                val ovlIdx = it.getColumnIndexOrThrow(COL_OVERLAY_SHOWN)

                while (it.moveToNext()) {
                    list.add(
                        UpdateDiagnosticRecord(
                            id = it.getLong(idIdx),
                            timestamp = it.getLong(tsIdx),
                            packageName = it.getString(pkgIdx),
                            category = it.getString(catIdx),
                            classification = it.getString(clsIdx),
                            decisionReason = it.getString(rsnIdx),
                            blockResult = it.getString(resIdx),
                            overlayShown = it.getInt(ovlIdx) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo diagnósticos: ${e.message}")
        }
        return list
    }

    private fun pruneOld(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM $TABLE_DIAG WHERE $COL_ID NOT IN (
                SELECT $COL_ID FROM $TABLE_DIAG ORDER BY $COL_TIMESTAMP DESC LIMIT $MAX_RECORDS
            )
            """.trimIndent()
        )
    }
}

