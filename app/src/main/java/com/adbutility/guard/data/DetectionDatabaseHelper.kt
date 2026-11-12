package com.adbutility.guard.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Gestor SQLite persistente para almacenar el historial de detecciones localmente.
 * Diseñado para bajo consumo de RAM y almacenamiento seguro sin dependencias externas.
 */
class DetectionDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "adbutility_guard.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_EVENTS = "detection_events"
        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_PACKAGE_NAME = "package_name"
        const val COL_DETECTION_TYPE = "detection_type"
        const val COL_ACTION_TAKEN = "action_taken"

        private const val MAX_LOG_ENTRIES = 500
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_PACKAGE_NAME TEXT NOT NULL,
                $COL_DETECTION_TYPE TEXT NOT NULL,
                $COL_ACTION_TAKEN TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
        db.execSQL("CREATE INDEX idx_events_timestamp ON $TABLE_EVENTS ($COL_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        onCreate(db)
    }

    @Synchronized
    fun insertEvent(event: DetectionEvent): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, event.timestamp)
            put(COL_PACKAGE_NAME, event.packageName)
            put(COL_DETECTION_TYPE, event.detectionType)
            put(COL_ACTION_TAKEN, event.actionTaken)
        }
        val newId = db.insert(TABLE_EVENTS, null, values)

        // Limpieza automática para mantener la base de datos ligera
        pruneOldLogs(db)
        return newId
    }

    @Synchronized
    fun getAllEvents(limit: Int = 100): List<DetectionEvent> {
        val list = mutableListOf<DetectionEvent>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_EVENTS,
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
            val timeIdx = it.getColumnIndexOrThrow(COL_TIMESTAMP)
            val pkgIdx = it.getColumnIndexOrThrow(COL_PACKAGE_NAME)
            val typeIdx = it.getColumnIndexOrThrow(COL_DETECTION_TYPE)
            val actIdx = it.getColumnIndexOrThrow(COL_ACTION_TAKEN)

            while (it.moveToNext()) {
                list.add(
                    DetectionEvent(
                        id = it.getLong(idIdx),
                        timestamp = it.getLong(timeIdx),
                        packageName = it.getString(pkgIdx),
                        detectionType = it.getString(typeIdx),
                        actionTaken = it.getString(actIdx)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getLatestEvent(): DetectionEvent? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EVENTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            "1"
        )

        cursor.use {
            if (it.moveToFirst()) {
                return DetectionEvent(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    packageName = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE_NAME)),
                    detectionType = it.getString(it.getColumnIndexOrThrow(COL_DETECTION_TYPE)),
                    actionTaken = it.getString(it.getColumnIndexOrThrow(COL_ACTION_TAKEN))
                )
            }
        }
        return null
    }

    @Synchronized
    fun getEventsCount(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_EVENTS", null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return 0L
    }

    @Synchronized
    fun clearAllEvents() {
        val db = writableDatabase
        db.delete(TABLE_EVENTS, null, null)
    }

    private fun pruneOldLogs(db: SQLiteDatabase) {
        val pruneQuery = """
            DELETE FROM $TABLE_EVENTS WHERE $COL_ID NOT IN (
                SELECT $COL_ID FROM $TABLE_EVENTS ORDER BY $COL_TIMESTAMP DESC LIMIT $MAX_LOG_ENTRIES
            )
        """.trimIndent()
        db.execSQL(pruneQuery)
    }
}

