package com.bandcompanion.app.export

import android.content.Context
import com.bandcompanion.core.database.repository.SampleRepository
import com.bandcompanion.core.database.repository.SleepRepository
import com.bandcompanion.core.database.repository.SummaryRepository
import com.bandcompanion.core.model.SampleMetric
import com.bandcompanion.core.model.SleepSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles autonomous, privacy-preserving data exports in CSV and JSON formats.
 */
class ExportManager(
    private val context: Context,
    private val sampleRepo: SampleRepository,
    private val sleepRepo: SleepRepository,
    private val summaryRepo: SummaryRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Exports minute-by-minute telemetry to CSV.
     */
    suspend fun exportSamplesToCsv(deviceId: String, startTime: Long, endTime: Long): File = withContext(Dispatchers.IO) {
        val samples = sampleRepo.getSamplesBetweenSync(deviceId, startTime, endTime)
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(exportDir, "fitness_samples_${System.currentTimeMillis()}.csv")

        FileWriter(file).use { writer ->
            writer.append("timestamp,date_time_utc,steps,heart_rate,spo2_percent,intensity,calories,source\n")
            for (s in samples) {
                val dt = dateFormat.format(Date(s.timestamp * 1000L))
                writer.append("${s.timestamp},\"$dt\",${s.steps},${s.heartRate ?: ""},${s.spo2 ?: ""},${s.intensity},${s.calories},${s.source}\n")
            }
        }
        file
    }

    /**
     * Exports TruSleep sessions to CSV.
     */
    suspend fun exportSleepToCsv(deviceId: String, startTime: Long, endTime: Long): File = withContext(Dispatchers.IO) {
        val sessions = sleepRepo.getSleepSessionsBetween(deviceId, startTime, endTime)
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(exportDir, "sleep_sessions_${System.currentTimeMillis()}.csv")

        FileWriter(file).use { writer ->
            writer.append("session_id,start_time,end_time,duration_minutes,sleep_score,efficiency_pct,latency_minutes,deep_sleep_minutes,rem_sleep_minutes,light_sleep_minutes,awake_minutes,avg_hrv_ms,hrv_baseline_min,hrv_baseline_max,avg_spo2_pct,avg_breath_rate\n")
            // Fetch latest sessions
            val latest = sleepRepo.getLatestSleepSessionWithStages(deviceId)
            if (latest != null) {
                val sDt = dateFormat.format(Date(latest.startTimestamp * 1000L))
                val eDt = dateFormat.format(Date(latest.endTimestamp * 1000L))
                writer.append("\"${latest.sessionId}\",\"$sDt\",\"$eDt\",${latest.durationMinutes},${latest.sleepScore ?: ""},${latest.sleepEfficiency ?: ""},${latest.latencyMinutes ?: ""},${latest.deepSleepMinutes},${latest.remSleepMinutes},${latest.lightSleepMinutes},${latest.awakeMinutes},${latest.avgHrv ?: ""},${latest.minHrvBaseline ?: ""},${latest.maxHrvBaseline ?: ""},${latest.avgSpo2 ?: ""},${latest.avgBreathRate ?: ""}\n")
            }
        }
        file
    }

    /**
     * Exports full dataset into a consolidated JSON bundle.
     */
    suspend fun exportFullJson(deviceId: String): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(exportDir, "band_companion_export_${System.currentTimeMillis()}.json")

        val root = JSONObject().apply {
            put("exported_at", System.currentTimeMillis())
            put("device_id", deviceId)
            put("version", "1.0.0")

            // Add recent daily summaries
            val summaries = summaryRepo.getRecentSummariesSync(deviceId, 30)
            val sumArray = JSONArray()
            for (sum in summaries) {
                sumArray.put(JSONObject().apply {
                    put("date", sum.date)
                    put("steps", sum.totalSteps)
                    put("resting_hr", sum.restingHeartRate)
                    put("recovery_score", sum.recoveryScore)
                    put("day_strain", sum.dayStrain)
                    put("acwr_ratio", sum.acwrRatio)
                })
            }
            put("daily_summaries", sumArray)
        }

        FileWriter(file).use { it.write(root.toString(2)) }
        file
    }
}
