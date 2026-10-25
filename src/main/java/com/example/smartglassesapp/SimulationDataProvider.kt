
package com.example.smartglassesapp

class SimulationDataProvider {

    private var sampleIndex = 0

    private val distances = listOf(
        250.0,
        150.0,
        80.0,
        35.0,
        180.0
    )

    fun nextSnapshot(): SensorSnapshot {
        val distance = distances[
            sampleIndex % distances.size
        ]
        sampleIndex++

        return SensorSnapshot(
            deviceId = "SIMULATED-ESP32",
            timestampMs = System.currentTimeMillis(),
            leftDistanceCm = distance,
            rightDistanceCm = distance + 15.0,
            heartRateBpm = null,
            obstacleDetected = distance <= 100.0,
            isSimulated = true,
            message = "SIMULATION ONLY — no physical sensor connected"
        )
    }

    fun reset() {
        sampleIndex = 0
    }
}
