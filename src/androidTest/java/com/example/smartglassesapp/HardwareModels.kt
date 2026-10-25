
package com.example.smartglassesapp

data class SensorSnapshot(
    val deviceId: String,
    val timestampMs: Long,
    val leftDistanceCm: Double?,
    val rightDistanceCm: Double?,
    val heartRateBpm: Int?,
    val obstacleDetected: Boolean,
    val isSimulated: Boolean,
    val message: String
) {
    fun hasValidDistance(): Boolean {
        return leftDistanceCm != null || rightDistanceCm != null
    }

    fun nearestDistanceCm(): Double? {
        val values = listOfNotNull(leftDistanceCm, rightDistanceCm)
        return values.minOrNull()
    }

    fun isStale(maxAgeMs: Long = 5000L): Boolean {
        return System.currentTimeMillis() - timestampMs > maxAgeMs
    }
}

data class HardwareConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val message: String = "Hardware not connected"
)
