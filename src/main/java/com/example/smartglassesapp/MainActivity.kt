package com.example.smartglassesapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ============================================================
// AI DETECTION DATA
// ============================================================

data class AiDetection(
    val objectName: String,
    val confidence: Double,
    val distance: Double?,
    val direction: String,
    val risk: String,
    val isLive: Boolean = false
)

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient:
            com.google.android.gms.location.FusedLocationProviderClient

    private var gpsStatus by mutableStateOf("Not connected")
    private var latitude by mutableStateOf<Double?>(null)
    private var longitude by mutableStateOf<Double?>(null)

    private lateinit var textToSpeech: TextToSpeech
    private var ttsReady by mutableStateOf(false)
    // Response speech follows the language detected in the latest recognized utterance.
    private var responseLocale: Locale = Locale("en", "IN")
    // Small local conversation context; no external AI service or UI change required.
    private var lastConversationTopic: String = ""

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening by mutableStateOf(false)
    private var recognizedText by mutableStateOf("")
    private var voiceStatus by mutableStateOf("Starting hands-free assistant")
    private var partialSpeechText by mutableStateOf("")
    private var recognizerActive = false

    // Hands-free state
    private var handsFreeEnabled = true
    private var shouldRestartListening = false
    private var isSpeaking = false
    private var restartListeningRunnable: Runnable? = null

    private var obstacleDetected by mutableStateOf(false)

    // ------------------------------------------------------------
    // LIVE CAMERA + ML KIT OBJECT DETECTION
    // ------------------------------------------------------------
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var cameraReady by mutableStateOf(false)
    private var cameraStatus by mutableStateOf("Camera not started")
    private var latestDetection by mutableStateOf<AiDetection?>(null)
    private var manualTestMode = false

    private val objectDetector by lazy {
        val options =
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build()

        ObjectDetection.getClient(options)
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                cameraStatus = "Starting camera..."
                startCamera()
            } else {
                cameraReady = false
                cameraStatus = "Camera permission denied"
            }
        }

    // ------------------------------------------------------------
    // HEART RATE (DEMO / SOFTWARE TEST)
    // ------------------------------------------------------------
    // This value is currently simulated. MAX30102 hardware integration
    // will replace it later.
    private var heartRate by mutableStateOf(72)
    private var heartRateStatus by mutableStateOf("Demo value — sensor not connected")
    private var showHeartRateScreen by mutableStateOf(false)

    private val demoDetection = AiDetection(
        objectName = "Person",
        confidence = 0.91,
        distance = 1.2,
        direction = "Front",
        risk = "High"
    )

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocation =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseLocation =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineLocation || coarseLocation) {
                getCurrentLocation()
            } else {
                gpsStatus = "Permission denied"
                speak(
                    "Location permission was denied. " +
                            "GPS navigation is unavailable."
                )
            }
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                handsFreeEnabled = true
                shouldRestartListening = true
                voiceStatus = "Hands-free assistant ready"
                startListeningAfterDelay(700)
            } else {
                handsFreeEnabled = false
                shouldRestartListening = false
                voiceStatus = "Microphone permission denied"
                speak(
                    "Microphone permission is required for hands-free voice assistance."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        cameraPreviewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        initializeTextToSpeech()

        setContent {
            if (showHeartRateScreen) {
                HeartRateScreen(
                    heartRate = heartRate,
                    status = heartRateStatus,
                    onBack = {
                        showHeartRateScreen = false
                    },
                    onTestHeartRate = {
                        heartRate = 78
                        heartRateStatus = "Test reading — MAX30102 not connected"
                    }
                )
            } else {
                SmartGlassesHomeScreen(
                    gpsStatus = gpsStatus,
                    latitude = latitude,
                    longitude = longitude,
                    ttsReady = ttsReady,
                    isListening = isListening,
                    recognizedText = recognizedText,
                    voiceStatus = voiceStatus,
                    obstacleDetected = obstacleDetected,
                    heartRate = heartRate,
                    detection = latestDetection ?: demoDetection,
                    cameraReady = cameraReady,
                    cameraStatus = cameraStatus,
                    cameraPreviewView = cameraPreviewView,
                    onEnableCamera = { requestCameraPermission() },

                    // Manual fallback. The assistant normally starts automatically.
                    onVoiceAssistant = {
                        requestMicrophonePermission()
                    },

                    onStopListening = {
                        stopHandsFreeAssistant()
                    },

                    onGetLocation = {
                        requestLocationPermission()
                    },

                    onNavigation = {
                        requestLocationPermission()
                        speak(
                            "Navigation mode activated. GPS guidance is being prepared."
                        )
                    },

                    onEmergency = {
                        speak(
                            "Emergency mode activated. " +
                                    "Please contact your emergency contact or emergency services."
                        )
                    },

                    onTestObstacle = {
                        manualTestMode = true
                        latestDetection = demoDetection.copy(isLive = false)
                        obstacleDetected = true
                        speakObstacleWarning()
                    },

                    onHeartRate = {
                        showHeartRateScreen = true
                    }
                )
            }
        }

        // Start the hands-free assistant automatically after the UI is created.
        window.decorView.postDelayed({
            requestMicrophonePermission()
        }, 1000)

        window.decorView.postDelayed({
            requestCameraPermission()
        }, 2200)
    }

    // ============================================================
    // CAMERA + ML KIT
    // ============================================================

    private fun requestCameraPermission() {
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraStatus = "Camera permission required"
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = cameraPreviewView.surfaceProvider
                }

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzeFrame(imageProxy)
                            }
                        }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                cameraReady = true
                cameraStatus = "Live camera + AI detection ready"
            } catch (e: Exception) {
                cameraReady = false
                cameraStatus = "Camera error"
                Log.e("SmartGlasses", "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        objectDetector
            .process(image)
            .addOnSuccessListener { objects ->
                updateLatestDetection(objects, imageProxy.width)
            }
            .addOnFailureListener { error ->
                Log.e("SmartGlasses", "ML Kit detection failed", error)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun updateLatestDetection(
        objects: List<DetectedObject>,
        imageWidth: Int
    ) {
        if (objects.isEmpty() || imageWidth <= 0) {
            if (!manualTestMode) {
                latestDetection = null
                obstacleDetected = false
            }
            return
        }

        manualTestMode = false

        val bestObject = objects.maxByOrNull { obj ->
            val box = obj.boundingBox
            box.width().toDouble() * box.height().toDouble()
        } ?: return

        val box = bestObject.boundingBox
        val centerX = box.centerX().toFloat() / imageWidth.toFloat()
        val widthRatio = box.width().toFloat() / imageWidth.toFloat()

        val direction = when {
            centerX < 0.33f -> "Left"
            centerX > 0.67f -> "Right"
            else -> "Front"
        }

        val risk = when {
            widthRatio >= 0.50f -> "High"
            widthRatio >= 0.25f -> "Medium"
            else -> "Low"
        }

        val label = bestObject.labels
            .maxByOrNull { it.confidence }
            ?.text
            ?.takeIf { it.isNotBlank() }
            ?: "Object"

        val confidence = bestObject.labels
            .maxByOrNull { it.confidence }
            ?.confidence
            ?.toDouble()
            ?: 0.0

        latestDetection = AiDetection(
            objectName = label,
            confidence = confidence,
            distance = null,
            direction = direction,
            risk = risk,
            isLive = true
        )

        obstacleDetected = true
    }

    // ============================================================
    // TEXT TO SPEECH
    // ============================================================

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale("en", "IN"))

                if (
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    textToSpeech.setSpeechRate(0.95f)
                    ttsReady = true

                    textToSpeech.setOnUtteranceProgressListener(
                        object : android.speech.tts.UtteranceProgressListener() {

                            override fun onStart(utteranceId: String?) {
                                isSpeaking = true
                            }

                            override fun onDone(utteranceId: String?) {
                                isSpeaking = false

                                if (
                                    utteranceId == "SmartGlassesTTS" &&
                                    handsFreeEnabled &&
                                    shouldRestartListening
                                ) {
                                    voiceStatus = "Listening again..."
                                    startListeningAfterDelay(650)
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                isSpeaking = false

                                if (
                                    handsFreeEnabled &&
                                    shouldRestartListening
                                ) {
                                    startListeningAfterDelay(700)
                                }
                            }
                        }
                    )

                }
            } else {
                ttsReady = false
                voiceStatus = "Voice output unavailable"
            }
        }
    }

    // ============================================================
    // SPEECH RECOGNIZER
    // ============================================================

    private fun createSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = null
            voiceStatus = "Speech recognition unavailable"
            return
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()

            speechRecognizer = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }

            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        } catch (e: Exception) {
            speechRecognizer = null
            voiceStatus = "Could not start speech recognition"
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                if (handsFreeEnabled && shouldRestartListening) {
                    isListening = true
                    voiceStatus = "Listening... Speak now"
                }
            }

            override fun onBeginningOfSpeech() {
                isListening = true
                voiceStatus = "Listening..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                voiceStatus = "Processing speech..."
            }

            override fun onError(error: Int) {
                isListening = false
                recognizerActive = false
                Log.w("SmartGlassesVoice", "Speech recognition error: $error")

                if (!handsFreeEnabled || !shouldRestartListening) return

                voiceStatus = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard — listening again"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waiting for your voice..."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone error — restarting"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service unavailable — retrying"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice service busy — restarting"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    else -> "Voice error $error — restarting"
                }

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    handsFreeEnabled = false
                    shouldRestartListening = false
                    return
                }

                startListeningAfterDelay(
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000 else 500
                )
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                recognizerActive = false
                Log.d("SmartGlassesVoice", "Final speech result received")

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val command = matches?.firstOrNull()?.trim().orEmpty()
                Log.d("SmartGlassesVoice", "Recognized command: $command")

                if (command.isNotBlank()) {
                    recognizedText = command
                    partialSpeechText = ""
                    voiceStatus = "Heard: $command"
                    processVoiceCommand(command)
                } else {
                    partialSpeechText = ""
                    voiceStatus = "No final speech detected — listening again"
                    startListeningAfterDelay(700)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    partialSpeechText = partial
                    recognizedText = partial
                    voiceStatus = "Hearing: $partial"
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // ============================================================
    // MICROPHONE PERMISSION
    // ============================================================

    private fun requestMicrophonePermission() {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            handsFreeEnabled = true
            shouldRestartListening = true
            voiceStatus = "Hands-free assistant ready"

            if (!isListening && !isSpeaking) {
                startListeningAfterDelay(250)
            }
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    // ============================================================
    // HANDS-FREE LISTENING
    // ============================================================

    private fun startListeningAfterDelay(delayMillis: Long) {
        restartListeningRunnable?.let {
            window.decorView.removeCallbacks(it)
        }

        val runnable = Runnable {
            if (
                handsFreeEnabled &&
                shouldRestartListening &&
                !isListening &&
                !isSpeaking
            ) {
                startListening()
            }
        }

        restartListeningRunnable = runnable
        window.decorView.postDelayed(runnable, delayMillis)
    }

    private fun startListening() {
        if (!handsFreeEnabled || !shouldRestartListening || isSpeaking) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            handsFreeEnabled = false
            shouldRestartListening = false
            voiceStatus = "Microphone permission required"
            return
        }

        createSpeechRecognizer()

        val recognizer = speechRecognizer ?: return

        try {
            // Keep the first recognition pass simple and reliable. Language switching
            // extras are not supported by every Android speech-recognition service.
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }

            recognizerActive = true
            voiceStatus = "Listening... Speak now"
            Log.d("SmartGlassesVoice", "Starting speech recognition")
            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            recognizerActive = false
            voiceStatus = "Could not start microphone — retrying"
            startListeningAfterDelay(1200)
        }
    }

    // Manual stop is still available for development/testing.
    private fun stopHandsFreeAssistant() {
        handsFreeEnabled = false
        shouldRestartListening = false
        isListening = false
        voiceStatus = "Voice assistant paused"

        restartListeningRunnable?.let {
            window.decorView.removeCallbacks(it)
        }

        speechRecognizer?.cancel()
        partialSpeechText = ""
        if (::textToSpeech.isInitialized) textToSpeech.stop()
    }

    // ============================================================
    // VOICE COMMAND PROCESSING
    // ============================================================

    private fun detectResponseLocale(text: String): Locale = when {
        text.any { it.code in 0x0B80..0x0BFF } -> Locale("ta", "IN")
        text.any { it.code in 0x0C00..0x0C7F } -> Locale("te", "IN")
        text.any { it.code in 0x0900..0x097F } -> Locale("hi", "IN")
        else -> Locale("en", "IN")
    }

    private fun localized(en: String, ta: String, te: String, hi: String): String = when (responseLocale.language) {
        "ta" -> ta
        "te" -> te
        "hi" -> hi
        else -> en
    }

    private fun processVoiceCommand(command: String) {
        responseLocale = detectResponseLocale(command)
        if (::textToSpeech.isInitialized && ttsReady) {
            val languageResult = textToSpeech.setLanguage(responseLocale)
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                responseLocale = Locale("en", "IN")
                textToSpeech.setLanguage(responseLocale)
            }
        }

        val text = command.lowercase(Locale.ROOT).trim()
            .replace("infront", "in front")
            .replace("what's", "what is")
            .replace("i'm", "i am")

        val hello = text.contains("hello") || text == "hi" || text.startsWith("hi ") ||
                text.contains("hey") || text.contains("வணக்கம்") || text.contains("ஹலோ") ||
                text.contains("నమస్కారం") || text.contains("హలో") || text.contains("नमस्ते") || text.contains("हेलो")
        val askFront = text.contains("what is in front") || text.contains("what is in the front") ||
                text.contains("what do you see") || text.contains("what can you see") ||
                text.contains("what is ahead") || text.contains("what is there") ||
                text == "what is" || text == "what" || text.contains("obstacle") ||
                text.contains("any obstacle") || text.contains("தடை") || text.contains("முன்னால்") ||
                text.contains("என்ன இருக்கிறது") || text.contains("అడ్డంకి") || text.contains("ముందు") ||
                text.contains("ఏముంది") || text.contains("बाधा") || text.contains("सामने") || text.contains("क्या है")
        val location = text.contains("where am i") || text.contains("my location") ||
                text.contains("where are we") || text == "location" || text.contains("என் இருப்பிடம்") ||
                text.contains("இருப்பிடம்") || text.contains("నా స్థానం") || text.contains("స్థానం") ||
                text.contains("मेरी लोकेशन") || text.contains("मेरा स्थान") || text.contains("लोकेशन")
        val navigation = text.contains("navigate") || text.contains("navigation") ||
                text.contains("directions") || text.contains("start navigation") ||
                text.contains("வழிகாட்டு") || text.contains("வழிசெலுத்து") || text.contains("నావిగేషన్") ||
                text.contains("దారి") || text.contains("नेविगेशन") || text.contains("रास्ता")
        val emergency = text.contains("emergency") || text.contains("help me") || text == "help" ||
                text.contains("danger") || text.contains("உதவி") || text.contains("அவசரம்") ||
                text.contains("సహాయం") || text.contains("అత్యవసరం") || text.contains("मदद") || text.contains("आपातकाल")
        val heart = text.contains("heart rate") || text.contains("heartbeat") || text.contains("pulse") ||
                text.contains("இதய துடிப்பு") || text.contains("இதயத் துடிப்பு") || text.contains("గుండె వేగం") ||
                text.contains("హృదయ స్పందన") || text.contains("दिल की धड़कन") || text.contains("नाड़ी")
        val status = text.contains("status") || text.contains("system") || text.contains("நிலை") ||
                text.contains("சிஸ்டம்") || text.contains("స్థితి") || text.contains("సిస్టమ్") ||
                text.contains("स्थिति") || text.contains("सिस्टम")
        val stop = text.contains("stop listening") || text.contains("pause assistant") ||
                text.contains("stop assistant") || text.contains("நிறுத்து") || text.contains("ஆபு") ||
                text.contains("ఆపు") || text.contains("बंद करो") || text.contains("रोकें")
        val followUp = text.contains("tell me more") || text.contains("more about it") ||
                text.contains("describe it") || text.contains("what else") || text.contains("அதைப் பற்றி") ||
                text.contains("ఇంకా చెప్పు") || text.contains("और बताओ")

        // Safety-related requests are handled before greetings and casual conversation.
        when {
            emergency -> {
                lastConversationTopic = "emergency"
                speak(localized(
                    "I'm here with you. Please contact your emergency contact or local emergency services now. I have not sent an SOS.",
                    "நான் உங்களுடன் இருக்கிறேன். உங்கள் அவசரத் தொடர்பையோ உள்ளூர் அவசர சேவையையோ இப்போது தொடர்புகொள்ளுங்கள். நான் SOS அனுப்பவில்லை.",
                    "నేను మీకు తోడుగా ఉన్నాను. వెంటనే మీ అత్యవసర సంప్రదింపువారిని లేదా స్థానిక అత్యవసర సేవలను సంప్రదించండి. నేను SOS పంపలేదు.",
                    "मैं आपके साथ हूँ। कृपया अभी अपने आपातकालीन संपर्क या स्थानीय आपातकालीन सेवा से संपर्क करें। मैंने SOS नहीं भेजा है।"
                ))
            }
            askFront -> {
                lastConversationTopic = "surroundings"
                speakFrontObject()
            }
            location -> {
                lastConversationTopic = "location"
                speakCurrentLocation()
            }
            navigation -> {
                lastConversationTopic = "navigation"
                requestLocationPermission()
                speak(localized(
                    "Of course. I'll help prepare GPS guidance. Please stay aware of your surroundings.",
                    "சரி, GPS வழிகாட்டுதலைத் தயாரிக்க உதவுகிறேன். சுற்றுப்புறத்தையும் கவனியுங்கள்.",
                    "తప్పకుండా. GPS మార్గదర్శకాన్ని సిద్ధం చేయడంలో సహాయం చేస్తాను. చుట్టూ ఉన్నవాటిని గమనించండి.",
                    "बिल्कुल। मैं GPS मार्गदर्शन तैयार करने में मदद करूँगा। आसपास का ध्यान रखें।"
                ))
            }
            heart -> {
                lastConversationTopic = "heart"
                speak(localized(
                    "The displayed heart rate is $heartRate beats per minute. The MAX30102 is not connected yet, so this is demo data.",
                    "திரையில் காட்டப்படும் இதயத் துடிப்பு நிமிடத்திற்கு $heartRate. MAX30102 இன்னும் இணைக்கப்படவில்லை; இது டெமோ மதிப்பு.",
                    "చూపిస్తున్న హృదయ స్పందన నిమిషానికి $heartRate. MAX30102 ఇంకా కనెక్ట్ కాలేదు; ఇది డెమో డేటా.",
                    "दिखाई गई हृदय गति $heartRate प्रति मिनट है। MAX30102 अभी जुड़ा లేదు, इसलिए यह डेमो डेटा है।"
                ))
            }
            status -> {
                lastConversationTopic = "status"
                speak(localized(
                    "I'm here and ready to help. Camera: $cameraStatus. GPS: $gpsStatus.",
                    "நான் உதவத் தயாராக இருக்கிறேன். கேமரா: $cameraStatus. GPS: $gpsStatus.",
                    "నేను సహాయం చేయడానికి సిద్ధంగా ఉన్నాను. కెమెరా: $cameraStatus. GPS: $gpsStatus.",
                    "मैं मदद के लिए तैयार हूँ। कैमरा: $cameraStatus। GPS: $gpsStatus।"
                ))
            }
            text.contains("test voice") || text.contains("test audio") || text.contains("குரல் சோதனை") ||
                    text.contains("వాయిస్ టెస్ట్") || text.contains("आवाज़ जांच") -> speak(localized(
                "Yep, you can hear me! Voice is working.", "ஆமாம், என் குரல் கேட்கிறதா? குரல் சரியாக வேலை செய்கிறது.",
                "అవును, నా గొంతు వినిపిస్తోందా? వాయిస్ సరిగ్గా పనిచేస్తోంది.", "हाँ, मेरी आवाज़ सुनाई दे रही है! आवाज़ ठीक काम कर रही है।"
            ))
            hello -> {
                lastConversationTopic = "greeting"
                speak(localized(
                    "Hey there! 😊 I'm SmartSight, your friendly companion. What can I help you with?",
                    "ஹாய்! 😊 நான் ஸ்மார்ட்சைட், உங்கள் நண்பன். என்ன உதவி வேண்டும்?",
                    "హాయ్! 😊 నేను స్మార్ట్‌సైట్, మీ స్నేహితుడిని. మీకు ఏం సహాయం కావాలి?",
                    "अरे, नमस्ते! 😊 मैं स्मार्टसाइट हूँ, आपका साथी। बताइए, कैसे मदद करूँ?"
                ))
            }
            text.contains("how are you") || text.contains("how are you doing") ||
                    text.contains("நலமா") || text.contains("ఎలా ఉన్నావు") || text.contains("कैसे हो") -> {
                lastConversationTopic = "smalltalk"
                speak(localized(
                    "I'm doing well, thanks for asking! Ready when you are. 😊",
                    "நான் நன்றாக இருக்கிறேன், கேட்டதற்கு நன்றி! நீங்கள் சொன்னால் தயார். 😊",
                    "నేను బాగున్నాను, అడిగినందుకు ధన్యవాదాలు! మీరు చెప్పగానే సిద్ధం. 😊",
                    "मैं बढ़िया हूँ, पूछने के लिए शुक्रिया! आप कहें, मैं तैयार हूँ। 😊"
                ))
            }
            text.contains("thank you") || text == "thanks" || text.contains("நன்றி") ||
                    text.contains("ధన్యవాదాలు") || text.contains("शुक्रिया") || text.contains("धन्यवाद") -> {
                lastConversationTopic = "thanks"
                speak(localized(
                    "You're always welcome. That's what I'm here for!",
                    "எப்போதும் வரவேற்கிறேன். உங்களுக்கு உதவத்தான் நான் இருக்கிறேன்!",
                    "ఎప్పుడైనా స్వాగతమే. మీకు సహాయం చేయడానికే నేను ఉన్నాను!",
                    "आपका हमेशा स्वागत है। मैं इसी लिए हूँ!"
                ))
            }
            text.contains("your name") || text.contains("who are you") || text.contains("உன் பெயர்") ||
                    text.contains("నీ పేరు") || text.contains("तुम्हारा नाम") -> speak(localized(
                "I'm SmartSight, your hands-free assistive companion. Think of me as your helpful sidekick!",
                "நான் ஸ்மார்ட்சைட், உங்களுக்கு உதவும் குரல் துணை. உங்கள் உதவியாளர் நண்பன் என்று நினைத்துக்கொள்ளுங்கள்!",
                "నేను స్మార్ట్‌సైట్, మీకు సహాయపడే హ్యాండ్స్-ఫ్రీ సహాయకుడిని. మీ చిన్న సహాయక స్నేహితుడిని అనుకోండి!",
                "मैं स्मार्टसाइट हूँ, आपका हैंड्स-फ्री सहायक साथी। मुझे अपना मददगार दोस्त समझिए!"
            ))
            text.contains("joke") || text.contains("make me laugh") || text.contains("சிரிக்க") ||
                    text.contains("నవ్వించు") || text.contains("चुटकुला") -> speak(localized(
                "Tiny tech joke: I wanted to tell you a joke about Wi-Fi, but it went over my head. 😄",
                "ஒரு சின்ன ஜோக்: Wi-Fi ஏன் அமைதியாக இருந்தது? அதற்கு நல்ல connection கிடைக்கவில்லை! 😄",
                "చిన్న జోక్: Wi-Fi ఎందుకు మాట్లాడలేదు? కనెక్షన్ బలహీనంగా ఉంది! 😄",
                "एक छोटा चुटकुला: Wi-Fi चुप क्यों था? उसका कनेक्शन कमजोर था! 😄"
            ))
            followUp && lastConversationTopic == "surroundings" -> speakFrontObject()
            text.contains("sorry") || text.contains("மன்னிக்க") || text.contains("క్షమించు") || text.contains("माफ़") -> speak(localized(
                "No worries at all. We're figuring it out together.", "பரவாயில்லை. நாம் சேர்ந்து பார்த்துக்கொள்ளலாம்.",
                "పర్లేదు. మనం కలిసి చూసుకుందాం.", "कोई बात नहीं। हम साथ में समझ लेंगे।"
            ))
            text == "bye" || text.contains("goodbye") || text.contains("போகிறேன்") ||
                    text.contains("వెళ్తున్నాను") || text.contains("अलविदा") -> speak(localized(
                "Take care! I'll be right here when you need me.", "கவனமாக இருங்கள்! தேவைப்பட்டால் நான் இங்கே இருக்கிறேன்.",
                "జాగ్రత్తగా ఉండండి! అవసరమైనప్పుడు నేను ఇక్కడే ఉంటాను.", "अपना ध्यान रखें! ज़रूरत पड़ने पर मैं यहीं हूँ।"
            ))
            stop -> {
                shouldRestartListening = false
                handsFreeEnabled = false
                speak(localized(
                    "Okay, I'll pause listening. You can resume me from the screen.",
                    "சரி, நான் கேட்பதை நிறுத்துகிறேன். திரையில் மீண்டும் இயக்கலாம்.",
                    "సరే, వినడం ఆపుతాను. స్క్రీన్ నుంచి మళ్లీ ప్రారంభించవచ్చు.",
                    "ठीक है, मैं सुनना रोकता हूँ। स्क्रीन से फिर शुरू कर सकते हैं।"
                ))
            }
            else -> speak(localized(
                "I may need a little more help understanding that. Try asking me about what's ahead, your location, navigation, or heart rate.",
                "அதைப் புரிந்துகொள்ள இன்னும் கொஞ்சம் உதவி வேண்டும். முன்னால் என்ன இருக்கிறது, இருப்பிடம், வழிகாட்டுதல் அல்லது இதயத் துடிப்பு பற்றி கேளுங்கள்.",
                "దాన్ని అర్థం చేసుకోవడానికి ఇంకొంచెం సహాయం కావాలి. ముందు ఏముంది, మీ స్థానం, నావిగేషన్ లేదా హృదయ స్పందన గురించి అడగండి.",
                "मुझे यह समझने में थोड़ी मदद चाहिए। आगे क्या है, लोकेशन, नेविगेशन या हृदय गति के बारे में पूछिए।"
            ))
        }
    }

    private fun speakFrontObject() {
        val detection = latestDetection

        if (detection == null) {
            speak(localized(
                "I cannot see a clear object right now. Please face the object and try again.",
                "தெளிவான பொருள் தெரியவில்லை. பொருளை நோக்கி மீண்டும் முயற்சிக்கவும்.",
                "ప్రస్తుతం స్పష్టమైన వస్తువు కనిపించడం లేదు. వస్తువు వైపు చూసి మళ్లీ ప్రయత్నించండి.",
                "अभी कोई स्पष्ट वस्तु दिखाई नहीं दे रही। वस्तु की ओर देखकर फिर कोशिश करें।"
            ))
            return
        }

        val objectName = detection.objectName
        val direction = detection.direction.lowercase(Locale.US)
        val confidence = if (detection.confidence > 0.0) {
            " Confidence ${(detection.confidence * 100).toInt()} percent."
        } else {
            ""
        }

        val distanceText = detection.distance?.let {
            " It is approximately ${"%.1f".format(it)} meters away."
        } ?: " Distance cannot be measured by the phone camera alone."

        speak(when (responseLocale.language) {
            "ta" -> "$objectName கண்டறியப்பட்டது. $direction திசையில் உள்ளது.$distanceText$confidence"
            "te" -> "$objectName గుర్తించబడింది. $direction వైపు ఉంది.$distanceText$confidence"
            "hi" -> "$objectName पहचाना गया। यह $direction दिशा में है।$distanceText$confidence"
            else -> "$objectName detected $direction of you.$distanceText$confidence"
        })
    }

    // ============================================================
    // OBSTACLE VOICE WARNING
    // ============================================================

    private fun speakObstacleWarning() {
        val detection = latestDetection ?: demoDetection.copy(isLive = false)
        obstacleDetected = true

        val distanceText = detection.distance?.let {
            "${"%.1f".format(it)} meters "
        } ?: ""

        val sourceText = if (detection.isLive) "live camera" else "test data"

        speak(when (responseLocale.language) {
            "ta" -> "எச்சரிக்கை. ${detection.objectName} ${distanceText}${detection.direction} திசையில் கண்டறியப்பட்டது. ஆபத்து நிலை ${detection.risk}. இது $sourceText தரவு."
            "te" -> "హెచ్చరిక. ${detection.objectName} ${distanceText}${detection.direction} వైపు గుర్తించబడింది. ప్రమాద స్థాయి ${detection.risk}. ఇది $sourceText సమాచారం."
            "hi" -> "चेतावनी। ${detection.objectName} ${distanceText}${detection.direction} दिशा में मिला। जोखिम स्तर ${detection.risk}। यह $sourceText डेटा है।"
            else -> "Warning. ${detection.objectName} detected ${distanceText}${detection.direction}. Risk level ${detection.risk}. This reading comes from $sourceText."
        })
    }

    // ============================================================
    // LOCATION
    // ============================================================

    private fun speakCurrentLocation() {
        if (latitude != null && longitude != null) {
            val lat = "%.4f".format(Locale.US, latitude)
            val lon = "%.4f".format(Locale.US, longitude)
            speak(when (responseLocale.language) {
                "ta" -> "உங்கள் தற்போதைய ஜிபிஎஸ் இருப்பிடம். அட்சரேகை $lat, தீர்க்கரேகை $lon."
                "te" -> "మీ ప్రస్తుత GPS స్థానం. అక్షాంశం $lat, రేఖాంశం $lon."
                "hi" -> "आपकी वर्तमान GPS लोकेशन। अक्षांश $lat, देशांतर $lon।"
                else -> "Your current GPS location is latitude $lat and longitude $lon."
            })
        } else {
            speak(localized(
                "Your location is not available yet. I will try to obtain your GPS location.",
                "உங்கள் இருப்பிடம் இன்னும் கிடைக்கவில்லை. ஜிபிஎஸ் இருப்பிடத்தைப் பெற முயற்சிக்கிறேன்.",
                "మీ స్థానం ఇంకా అందుబాటులో లేదు. GPS స్థానం పొందడానికి ప్రయత్నిస్తున్నాను.",
                "आपकी लोकेशन अभी उपलब्ध नहीं है। मैं GPS लोकेशन प्राप्त करने की कोशिश करूँगा।"
            ))
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission() {
        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        gpsStatus = "Locating..."

        val priority =
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(
                priority,
                cancellationTokenSource.token
            )
            .addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    gpsStatus = "Connected"

                    speak(localized(
                        "GPS connected. Your current location has been detected.",
                        "ஜிபிஎஸ் இணைக்கப்பட்டது. உங்கள் தற்போதைய இருப்பிடம் கண்டறியப்பட்டது.",
                        "GPS కనెక్ట్ అయింది. మీ ప్రస్తుత స్థానం గుర్తించబడింది.",
                        "GPS जुड़ गया है। आपकी वर्तमान लोकेशन मिल गई है।"
                    ))
                } else {
                    gpsStatus = "Unavailable"

                    speak(localized(
                        "GPS location is currently unavailable.",
                        "ஜிபிஎஸ் இருப்பிடம் தற்போது கிடைக்கவில்லை.",
                        "GPS స్థానం ప్రస్తుతం అందుబాటులో లేదు.",
                        "GPS लोकेशन अभी उपलब्ध नहीं है।"
                    ))
                }
            }
            .addOnFailureListener {
                gpsStatus = "GPS error"

                speak(localized(
                    "There was a problem obtaining your location.",
                    "உங்கள் இருப்பிடத்தைப் பெறுவதில் சிக்கல் ஏற்பட்டது.",
                    "మీ స్థానాన్ని పొందడంలో సమస్య వచ్చింది.",
                    "आपकी लोकेशन प्राप्त करने में समस्या हुई।"
                ))
            }
    }

    // ============================================================
    // SPEAK
    // ============================================================

    private fun speak(message: String) {
        if (!::textToSpeech.isInitialized || !ttsReady) {
            voiceStatus = "Voice output is not ready"
            return
        }

        val languageResult = textToSpeech.setLanguage(responseLocale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            responseLocale = Locale("en", "IN")
            textToSpeech.setLanguage(responseLocale)
        }

        // Never let the recognizer and TTS use the microphone/audio focus together.
        restartListeningRunnable?.let {
            window.decorView.removeCallbacks(it)
        }
        speechRecognizer?.cancel()
        isListening = false
        recognizerActive = false
        partialSpeechText = ""
        isSpeaking = true
        voiceStatus = "Speaking..."

        val result = textToSpeech.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "SmartGlassesTTS"
        )

        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            voiceStatus = "Voice output failed"
            if (handsFreeEnabled && shouldRestartListening) {
                startListeningAfterDelay(800)
            }
        }
    }

    // ============================================================
    // CLEAN UP
    // ============================================================

    override fun onPause() {
        super.onPause()

        // Do not continuously use the microphone when the app is not visible.
        // A later stage can move this into a foreground service for glasses use.
        if (isListening) {
            speechRecognizer?.cancel()
            isListening = false
        }

        restartListeningRunnable?.let {
            window.decorView.removeCallbacks(it)
        }

        if (::cameraPreviewView.isInitialized) {
            try {
                ProcessCameraProvider.getInstance(this).get().unbindAll()
            } catch (_: Exception) {
                // Ignore camera cleanup errors during pause.
            }
        }
        cameraReady = false
    }

    override fun onResume() {
        super.onResume()

        if (
            handsFreeEnabled &&
            shouldRestartListening &&
            ttsReady &&
            !isListening &&
            !isSpeaking
        ) {
            startListeningAfterDelay(700)
        }

        if (::cameraPreviewView.isInitialized &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    override fun onDestroy() {
        handsFreeEnabled = false
        shouldRestartListening = false

        restartListeningRunnable?.let {
            window.decorView.removeCallbacks(it)
        }

        speechRecognizer?.destroy()

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        try {
            objectDetector.close()
        } catch (_: Exception) {
            // Ignore detector cleanup errors.
        }

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        super.onDestroy()
    }
}

// ============================================================
// MAIN UI
// ============================================================

@Composable
fun SmartGlassesHomeScreen(

    gpsStatus: String,

    latitude: Double?,

    longitude: Double?,

    ttsReady: Boolean,

    isListening: Boolean,

    recognizedText: String,

    voiceStatus: String,

    obstacleDetected: Boolean,

    heartRate: Int,

    detection: AiDetection,

    cameraReady: Boolean,

    cameraStatus: String,

    cameraPreviewView: PreviewView,

    onEnableCamera: () -> Unit,

    onVoiceAssistant: () -> Unit,

    onStopListening: () -> Unit,

    onGetLocation: () -> Unit,

    onNavigation: () -> Unit,

    onEmergency: () -> Unit,

    onTestObstacle: () -> Unit,

    onHeartRate: () -> Unit

) {

    // --------------------------------------------------------
    // COLORS
    // --------------------------------------------------------

    val background =
        Color(0xFFF4F7FB)

    val primaryBlue =
        Color(0xFF155EEF)

    val darkText =
        Color(0xFF172033)

    val secondaryText =
        Color(0xFF667085)

    val successGreen =
        Color(0xFF12B76A)

    val dangerRed =
        Color(0xFFD92D20)

    val softBlue =
        Color(0xFFEAF2FF)

    val softGreen =
        Color(0xFFE9F8F1)

    val softRed =
        Color(0xFFFFEFED)

    // --------------------------------------------------------
    // SCREEN
    // --------------------------------------------------------

    Surface(

        modifier =
            Modifier.fillMaxSize(),

        color =
            background

    ) {

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 18.dp
                    )

        ) {

            // =================================================
            // HEADER
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Column {

                    Text(

                        text =
                            "SMARTSIGHT",

                        fontSize =
                            27.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            darkText
                    )

                    Text(

                        text =
                            "AI ASSISTIVE VISION",

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Medium,

                        color =
                            secondaryText,

                        letterSpacing =
                            1.5.sp
                    )
                }

                Box(

                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                softGreen
                            ),

                    contentAlignment =
                        Alignment.Center

                ) {

                    Box(

                        modifier =
                            Modifier
                                .size(13.dp)
                                .clip(
                                    CircleShape
                                )
                                .background(
                                    if (ttsReady)
                                        successGreen
                                    else
                                        dangerRed
                                )
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            // =================================================
            // VOICE ASSISTANT
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    ),

                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )

            ) {

                Column(

                    modifier =
                        Modifier.padding(20.dp)

                ) {

                    Text(

                        text =
                            "VOICE ASSISTANT",

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            secondaryText,

                        letterSpacing =
                            1.2.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Row(

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Box(

                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(
                                        CircleShape
                                    )
                                    .background(
                                        if (isListening)
                                            dangerRed
                                        else if (ttsReady)
                                            successGreen
                                        else
                                            secondaryText
                                    )
                        )

                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )

                        Column {

                            Text(

                                text =
                                    if (isListening)
                                        "Listening..."
                                    else
                                        voiceStatus,

                                fontSize =
                                    19.sp,

                                fontWeight =
                                    FontWeight.Bold,

                                color =
                                    darkText
                            )

                            if (
                                recognizedText.isNotEmpty()
                            ) {

                                Spacer(
                                    modifier =
                                        Modifier.height(4.dp)
                                )

                                Text(

                                    text =
                                        "\"$recognizedText\"",

                                    fontSize =
                                        13.sp,

                                    color =
                                        secondaryText
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )

                    if (isListening) {

                        Button(

                            onClick =
                                onStopListening,

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),

                            shape =
                                RoundedCornerShape(16.dp),

                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        dangerRed
                                )

                        ) {

                            Text(

                                text =
                                    "STOP LISTENING",

                                fontSize =
                                    15.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (detection.isLive)
                                "Source: live phone camera (ML Kit)"
                            else
                                "Source: test data",
                            fontSize = 11.sp,
                            color = secondaryText
                        )

                    } else {

                        Button(

                            onClick =
                                onVoiceAssistant,

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),

                            shape =
                                RoundedCornerShape(16.dp),

                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        primaryBlue
                                )

                        ) {

                            Text(

                                text =
                                    "START VOICE ASSISTANT",

                                fontSize =
                                    15.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Text(

                        text =
                            "Try: \"What is in front of me?\" " +
                                    "\"Where am I?\" " +
                                    "or \"Start navigation.\"",

                        modifier =
                            Modifier.fillMaxWidth(),

                        fontSize =
                            12.sp,

                        color =
                            secondaryText,

                        textAlign =
                            TextAlign.Center,

                        lineHeight =
                            18.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // =================================================
            // SYSTEM STATUS
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )

            ) {

                Column(

                    modifier =
                        Modifier.padding(20.dp)

                ) {

                    Text(

                        text =
                            "SYSTEM STATUS",

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            secondaryText,

                        letterSpacing =
                            1.2.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Row(

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Box(

                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .clip(
                                        CircleShape
                                    )
                                    .background(
                                        if (ttsReady)
                                            successGreen
                                        else
                                            dangerRed
                                    )
                        )

                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )

                        Column {

                            Text(

                                text =
                                    if (ttsReady)
                                        "Voice system ready"
                                    else
                                        "Starting voice system...",

                                fontSize =
                                    19.sp,

                                fontWeight =
                                    FontWeight.Bold,

                                color =
                                    darkText
                            )

                            Text(

                                text =
                                    "Speech input and voice output",

                                fontSize =
                                    13.sp,

                                color =
                                    secondaryText
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // =================================================
            // LIVE CAMERA
            // =================================================

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIVE CAMERA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryText,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = cameraStatus,
                                fontSize = 13.sp,
                                color = if (cameraReady) successGreen else secondaryText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(
                                    if (cameraReady) successGreen else secondaryText
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF101828))
                    ) {
                        if (cameraReady) {
                            AndroidView<PreviewView>(
                                factory = { _: android.content.Context -> cameraPreviewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "Camera preview unavailable",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!cameraReady) {
                        OutlinedButton(
                            onClick = onEnableCamera,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "ENABLE CAMERA",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }


                    Text(
                        text = "Say: \"What is in front of me?\" to ask the live detector.",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 12.sp,
                        color = secondaryText,
                        textAlign = TextAlign.Center
                    )

                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =================================================
            // ENVIRONMENT
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (obstacleDetected)
                                softRed
                            else
                                softBlue
                    )

            ) {

                Column(

                    modifier =
                        Modifier.padding(20.dp)

                ) {

                    Row(

                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween

                    ) {

                        Text(

                            text =
                                "ENVIRONMENT",

                            fontSize =
                                12.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                if (obstacleDetected)
                                    dangerRed
                                else
                                    primaryBlue,

                            letterSpacing =
                                1.2.sp
                        )

                        Text(

                            text =
                                "AI",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                if (obstacleDetected)
                                    dangerRed
                                else
                                    primaryBlue
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(15.dp)
                    )

                    if (obstacleDetected) {

                        Text(

                            text =
                                "Obstacle detected",

                            fontSize =
                                23.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                darkText
                        )

                        Spacer(
                            modifier =
                                Modifier.height(5.dp)
                        )

                        Text(
                            text = buildString {
                                append(detection.objectName)
                                append(" • ")
                                append(detection.direction)
                                detection.distance?.let {
                                    append(" • ")
                                    append("%.1f m".format(it))
                                }
                            },
                            fontSize = 14.sp,
                            color = secondaryText
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Row(

                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)

                        ) {

                            InfoPill(

                                title =
                                    "RISK",

                                value =
                                    detection.risk,

                                background =
                                    softRed,

                                textColor =
                                    dangerRed
                            )

                            InfoPill(

                                title =
                                    "CONFIDENCE",

                                value =
                                    "${(detection.confidence * 100).toInt()}%",

                                background =
                                    Color.White,

                                textColor =
                                    darkText
                            )
                        }

                    } else {

                        Text(

                            text =
                                "Environment monitoring",

                            fontSize =
                                22.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                darkText
                        )

                        Spacer(
                            modifier =
                                Modifier.height(5.dp)
                        )

                        Text(

                            text =
                                if (cameraReady) "Camera is monitoring the environment." else "Waiting for camera or sensor data.",

                            fontSize =
                                14.sp,

                            color =
                                secondaryText
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            // =================================================
            // TEST OBSTACLE
            // =================================================

            OutlinedButton(

                onClick =
                    onTestObstacle,

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(14.dp)

            ) {

                Text(

                    text =
                        "TEST OBSTACLE VOICE ALERT",

                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // =================================================
            // GPS + HEALTH
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)

            ) {

                SmallStatusCard(

                    title =
                        "GPS",

                    value =
                        gpsStatus,

                    modifier =
                        Modifier.weight(1f),

                    statusColor =
                        if (
                            gpsStatus == "Connected"
                        )
                            successGreen
                        else
                            primaryBlue
                )

                SmallStatusCard(

                    title =
                        "HEALTH",

                    value =
                        "Ready",

                    modifier =
                        Modifier.weight(1f),

                    statusColor =
                        successGreen
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            // =================================================
            // HEART RATE
            // =================================================

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color.White
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "HEART RATE MONITOR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryText,
                        letterSpacing = 1.1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "♥",
                            fontSize = 32.sp,
                            color = dangerRed
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "$heartRate BPM",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = darkText
                            )
                            Text(
                                text = "Demo reading",
                                fontSize = 12.sp,
                                color = secondaryText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onHeartRate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "OPEN HEART RATE MONITOR",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // =================================================
            // LOCATION
            // =================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(20.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )

            ) {

                Column(

                    modifier =
                        Modifier.padding(18.dp)

                ) {

                    Text(

                        text =
                            "CURRENT LOCATION",

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            secondaryText,

                        letterSpacing =
                            1.1.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    if (
                        latitude != null &&
                        longitude != null
                    ) {

                        Text(

                            text =
                                "Latitude  %.6f"
                                    .format(latitude),

                            fontSize =
                                14.sp,

                            color =
                                darkText
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(

                            text =
                                "Longitude %.6f"
                                    .format(longitude),

                            fontSize =
                                14.sp,

                            color =
                                darkText
                        )

                    } else {

                        Text(

                            text =
                                "Location not detected",

                            fontSize =
                                14.sp,

                            color =
                                secondaryText
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedButton(

                        onClick =
                            onGetLocation,

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(12.dp)

                    ) {

                        Text(

                            text =
                                "UPDATE LOCATION",

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            // =================================================
            // NAVIGATION
            // =================================================

            Text(

                text =
                    "QUICK ACTIONS",

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    secondaryText,

                letterSpacing =
                    1.1.sp
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            Button(

                onClick =
                    onNavigation,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                shape =
                    RoundedCornerShape(16.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            primaryBlue
                    )

            ) {

                Text(

                    text =
                        "START NAVIGATION",

                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            // =================================================
            // EMERGENCY
            // =================================================

            Button(

                onClick =
                    onEmergency,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp),

                shape =
                    RoundedCornerShape(17.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            dangerRed
                    )

            ) {

                Text(

                    text =
                        "EMERGENCY SOS",

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.Bold,

                    letterSpacing =
                        0.8.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            // =================================================
            // ACCESSIBILITY MESSAGE
            // =================================================

            Text(

                text =
                    "Voice assistance is the primary interaction. " +
                            "The screen is provided mainly for setup " +
                            "and monitoring.",

                modifier =
                    Modifier.fillMaxWidth(),

                fontSize =
                    12.sp,

                color =
                    secondaryText,

                textAlign =
                    TextAlign.Center,

                lineHeight =
                    18.sp
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }
    }
}

// ============================================================
// HEART RATE SCREEN
// ============================================================

@Composable
fun HeartRateScreen(
    heartRate: Int,
    status: String,
    onBack: () -> Unit,
    onTestHeartRate: () -> Unit
) {
    val background = Color(0xFFF4F7FB)
    val darkText = Color(0xFF172033)
    val secondaryText = Color(0xFF667085)
    val dangerRed = Color(0xFFD92D20)
    val successGreen = Color(0xFF12B76A)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("BACK")
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "HEART RATE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkText
                    )
                    Text(
                        text = "HEALTH MONITORING",
                        fontSize = 11.sp,
                        color = secondaryText,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "♥",
                        fontSize = 64.sp,
                        color = dangerRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$heartRate",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkText
                    )
                    Text(
                        text = "BPM",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (heartRate in 60..100) "Normal test range" else "Check reading",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (heartRate in 60..100) successGreen else dangerRed
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SENSOR STATUS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryText,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(secondaryText)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status,
                            fontSize = 14.sp,
                            color = darkText
                        )
                    }
                }
            }

            Button(
                onClick = onTestHeartRate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF155EEF)
                )
            ) {
                Text(
                    text = "TEST HEART RATE",
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Current readings are demo values for software testing. " +
                        "The MAX30102 will provide real heart-rate data after hardware integration.",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp,
                color = secondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ============================================================
// INFO PILL
// ============================================================

@Composable
fun InfoPill(

    title: String,

    value: String,

    background: Color,

    textColor: Color

) {

    Column(

        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(12.dp)
                )
                .background(
                    background
                )
                .padding(
                    horizontal = 14.dp,
                    vertical = 9.dp
                )

    ) {

        Text(

            text =
                title,

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                textColor
        )

        Spacer(
            modifier =
                Modifier.height(2.dp)
        )

        Text(

            text =
                value,

            fontSize =
                14.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                textColor
        )
    }
}

// ============================================================
// SMALL STATUS CARD
// ============================================================

@Composable
fun SmallStatusCard(

    title: String,

    value: String,

    modifier: Modifier,

    statusColor: Color

) {

    Card(

        modifier =
            modifier,

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White
            )

    ) {

        Column(

            modifier =
                Modifier.padding(16.dp)

        ) {

            Text(

                text =
                    title,

                fontSize =
                    11.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF667085),

                letterSpacing =
                    1.sp
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Box(

                    modifier =
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                statusColor
                            )
                )

                Spacer(
                    modifier =
                        Modifier.width(7.dp)
                )

                Text(

                    text =
                        value,

                    fontSize =
                        14.sp,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        Color(0xFF172033)
                )
            }
        }
    }
}
