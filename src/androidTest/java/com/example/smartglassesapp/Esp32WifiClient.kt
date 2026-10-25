
package com.example.smartglassesapp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Esp32WifiClient(
    private val baseUrl: String = "http://192.168.4.1"
) {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    fun fetchStatus(
        callback: (Result<SensorSnapshot>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val url = URL("${baseUrl.trimEnd('/')}/api/status")
                val connection =
                    url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    val code = connection.responseCode

                    if (code !in 200..299) {
                        throw IllegalStateException(
                            "ESP32 returned HTTP $code"
                        )
                    }

                    val body = connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    val json = JSONObject(body)

                    fun optionalDouble(key: String): Double? {
                        if (!json.has(key) || json.isNull(key)) {
                            return null
                        }

                        val value = json.optDouble(key, Double.NaN)
                        return value.takeIf {
                            it.isFinite() && it >= 0.0
                        }
                    }

                    val left = optionalDouble("left_distance_cm")
                    val right = optionalDouble("right_distance_cm")

                    val heartRate = if (
                        json.has("heart_rate_bpm") &&
                        !json.isNull("heart_rate_bpm")
                    ) {
                        json.optInt("heart_rate_bpm", -1)
                            .takeIf { it in 30..220 }
                    } else {
                        null
                    }

                    val nearest = listOfNotNull(left, right)
                        .minOrNull()

                    SensorSnapshot(
                        deviceId = json.optString(
                            "device_id",
                            "ESP32-S3"
                        ),
                        timestampMs = System.currentTimeMillis(),
                        leftDistanceCm = left,
                        rightDistanceCm = right,
                        heartRateBpm = heartRate,
                        obstacleDetected = nearest != null &&
                                nearest <= 100.0,
                        isSimulated = false,
                        message = "Live ESP32 sensor response"
                    )
                } finally {
                    connection.disconnect()
                }
            }

            callback(result)
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
