package com.bandcompanion.app.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Autonomous, privacy-focused real-time weather provider.
 * Sourced directly from local device GPS/network location and Open-Meteo API.
 */
object RealWeatherProvider {

    private const val TAG = "RealWeatherProvider"

    // Default fallback coordinates (Kathmandu, Nepal)
    private const val DEFAULT_LAT = 27.677
    private const val DEFAULT_LON = 85.365
    private const val DEFAULT_CITY = "Kathmandu"

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentWeather(context: Context): WeatherCondition = withContext(Dispatchers.IO) {
        var lat = DEFAULT_LAT
        var lon = DEFAULT_LON
        var cityName = DEFAULT_CITY

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                var bestLocation: Location? = null
                for (p in providers) {
                    try {
                        val loc = locationManager.getLastKnownLocation(p)
                        if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                            bestLocation = loc
                        }
                    } catch (e: Exception) {
                        // Permission or disabled provider
                    }
                }

                if (bestLocation != null) {
                    lat = bestLocation.latitude
                    lon = bestLocation.longitude
                    Log.i(TAG, "Device location detected: ($lat, $lon)")
                }
            }

            // Reverse geocode to real city name
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        cityName = addr.locality
                            ?: addr.subAdminArea
                            ?: addr.adminArea
                            ?: DEFAULT_CITY
                        Log.i(TAG, "Resolved city name: $cityName")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder reverse-lookup error: ${e.message}")
            }

            // Query live weather from Open-Meteo API
            val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,uv_index"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BandCompanion/1.0 (Android)")
            }

            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val current = json.getJSONObject("current")

                val tempC = current.getDouble("temperature_2m").roundToInt()
                val code = current.getInt("weather_code")
                val humidity = current.optInt("relative_humidity_2m", 65)
                val uv = current.optDouble("uv_index", 3.0).toInt()

                val (huaweiCode, condName) = mapWeatherCode(code)

                Log.i(TAG, "Live weather fetched: $tempC°C, $condName, $cityName, UV=$uv")
                return@withContext WeatherCondition(
                    tempCelsius = tempC,
                    conditionCode = huaweiCode,
                    conditionName = condName,
                    location = cityName,
                    uvIndex = uv,
                    aqi = 42
                )
            } else {
                Log.w(TAG, "Open-Meteo returned HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live weather: ${e.message}", e)
        }

        // Resilient fallback with real city
        WeatherCondition(
            tempCelsius = 19,
            conditionCode = 1,
            conditionName = "Clear Sky",
            location = cityName,
            uvIndex = 1,
            aqi = 42
        )
    }

    /**
     * Maps WMO weather interpretation codes to Huawei Band LPv2 condition codes.
     */
    private fun mapWeatherCode(wmoCode: Int): Pair<Int, String> {
        return when (wmoCode) {
            0 -> Pair(1, "Clear Sky")
            1, 2 -> Pair(2, "Partly Cloudy")
            3 -> Pair(3, "Overcast")
            45, 48 -> Pair(4, "Foggy")
            51, 53, 55, 56, 57 -> Pair(7, "Drizzle")
            61, 63, 65 -> Pair(7, "Rain")
            66, 67 -> Pair(8, "Freezing Rain")
            71, 73, 75, 77 -> Pair(8, "Snow")
            80, 81, 82 -> Pair(7, "Showers")
            85, 86 -> Pair(8, "Snow Showers")
            95 -> Pair(9, "Thunderstorm")
            96, 99 -> Pair(9, "Heavy Thunderstorm")
            else -> Pair(2, "Cloudy")
        }
    }
}
