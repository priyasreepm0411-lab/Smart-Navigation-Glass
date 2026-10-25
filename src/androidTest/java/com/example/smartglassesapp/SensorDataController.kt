
package com.example.smartglassesapp

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SensorDataController(
    private val onSnapshot: (SensorSnapshot) -> Unit,
    private val onConnectionMessage: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    private val simulation = SimulationDataProvider()
    private var wifiClient: Esp32WifiClient? = null
    private var simulationTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var wifiTask: java.util.concurrent.ScheduledFuture<*>? = null
    private var lastObstacleState = false

    fun startSimulation() {
        stopAll()
        simulation.reset()
        onConnectionMessage(
            "SIMULATION MODE — hardware is not connected"
        )

        simulationTask = scheduler.scheduleAtFixedRate({
            val snapshot = simulation.nextSnapshot()
            mainHandler.post {
                onSnapshot(snapshot)
            }
        }, 0, 3, TimeUnit.SECONDS)
    }

    fun connectToEsp32(
        baseUrl: String = "http://192.168.4.1"
    ) {
        stopAll()
        wifiClient = Esp32WifiClient(baseUrl)

        onConnectionMessage(
            "Connecting to ESP32-S3 at $baseUrl"
        )

        wifiTask = scheduler.scheduleAtFixedRate({
            val client = wifiClient ?: return@scheduleAtFixedRate

            client.fetchStatus { result ->
                mainHandler.post {
                    result.onSuccess { snapshot ->
                        onConnectionMessage(
                            "Connected to ${snapshot.deviceId}"
                        )
                        onSnapshot(snapshot)
                    }.onFailure { error ->
                        onConnectionMessage(
                            "ESP32 unavailable: " +
                                    (error.message ?: "connection failed")
                        )
                    }
                }
            }
        }, 0, 2, TimeUnit.SECONDS)
    }

    fun stopAll() {
        simulationTask?.cancel(true)
        simulationTask = null

        wifiTask?.cancel(true)
        wifiTask = null

        wifiClient?.shutdown()
        wifiClient = null
    }

    fun close() {
        stopAll()
        scheduler.shutdownNow()
    }
}
