package com.adbutility.guard

import android.content.Context
import com.adbutility.guard.data.DetectionDatabaseHelper
import com.adbutility.guard.data.DetectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Registrador de eventos de detección.
 * Almacena en la base de datos local y actualiza métricas de preferencias.
 */
class EventLogger(context: Context) {

    private val dbHelper = DetectionDatabaseHelper(context.applicationContext)
    private val prefs = PreferencesManager(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logDetection(
        packageName: String,
        detectionType: String,
        actionTaken: String,
        onLogged: ((DetectionEvent) -> Unit)? = null
    ) {
        val event = DetectionEvent(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            detectionType = detectionType,
            actionTaken = actionTaken
        )

        prefs.recordDetection(packageName, detectionType, actionTaken)

        scope.launch {
            val id = dbHelper.insertEvent(event)
            val savedEvent = event.copy(id = id)
            onLogged?.invoke(savedEvent)
        }
    }

    fun updateAction(eventId: Long, newAction: String) {
        prefs.updateLastAction(newAction)
    }

    fun getRecentEvents(limit: Int = 100): List<DetectionEvent> {
        return dbHelper.getAllEvents(limit)
    }

    fun getLatestEvent(): DetectionEvent? {
        return dbHelper.getLatestEvent()
    }

    fun getEventsCount(): Long {
        return dbHelper.getEventsCount()
    }

    fun clearHistory() {
        dbHelper.clearAllEvents()
        prefs.resetCounters()
    }

    fun getHistoryAsJson(limit: Int = 50): JSONObject {
        val events = getRecentEvents(limit)
        val jsonArray = JSONArray()
        for (e in events) {
            jsonArray.put(e.toJson())
        }

        return JSONObject().apply {
            put("count", events.size)
            put("totalInDb", getEventsCount())
            put("events", jsonArray)
        }
    }
}

