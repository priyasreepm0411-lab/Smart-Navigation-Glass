package com.example.smartglassesapp

import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.max
import kotlin.math.min

data class DetectedItem(
    val name: String,
    val position: String,
    val risk: String,
    val confidence: Float
)

class CameraAnalyzer(
    private val onDetection:
        (List<DetectedItem>) -> Unit
) : ImageAnalysis.Analyzer {

    private val options =
        ObjectDetectorOptions.Builder()
            .setDetectorMode(
                ObjectDetectorOptions.STREAM_MODE
            )
            .enableMultipleObjects()
            .enableClassification()
            .build()

    private val detector =
        ObjectDetection.getClient(options)

    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage =
            imageProxy.image

        if (mediaImage == null) {

            imageProxy.close()

            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(image)
            .addOnSuccessListener { objects ->

                val results =
                    objects.mapNotNull { objectData ->

                        convertObject(
                            objectData,
                            imageProxy.width
                        )
                    }

                onDetection(results)
            }
            .addOnFailureListener {

                onDetection(emptyList())
            }
            .addOnCompleteListener {

                imageProxy.close()
            }
    }

    private fun convertObject(
        objectData: DetectedObject,
        imageWidth: Int
    ): DetectedItem? {

        val box: Rect =
            objectData.boundingBox

        val centerX =
            box.centerX().toFloat()

        val relativeX =
            centerX / imageWidth.toFloat()

        val position =
            when {

                relativeX < 0.33f ->
                    "left"

                relativeX > 0.66f ->
                    "right"

                else ->
                    "center"
            }

        val boxWidth =
            max(1, box.width())

        val widthRatio =
            boxWidth.toFloat() /
                    max(1, imageWidth).toFloat()

        val risk =
            when {

                widthRatio > 0.55f ->
                    "HIGH"

                widthRatio > 0.30f ->
                    "MEDIUM"

                else ->
                    "LOW"
            }

        val label =
            objectData.labels
                .maxByOrNull {
                    it.confidence
                }

        val name =
            label?.text ?: "object"

        val confidence =
            label?.confidence ?: 0f

        return DetectedItem(
            name = name,
            position = position,
            risk = risk,
            confidence = confidence
        )
    }
}