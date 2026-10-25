package com.example.smartglassesapp

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechHelper(
    private val context: Context
) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var handsFreeMode = false
    private var isListening = false

    private var currentLanguageTag = "en-IN"

    var onSpeechResult: ((String, String) -> Unit)? = null

    var onListeningChanged: ((Boolean) -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->

            if (status == TextToSpeech.SUCCESS) {

                setTtsLanguage("en-IN")

                textToSpeech?.setSpeechRate(1.0f)
            }
        }
    }

    // ---------------------------------------------------------
    // TEXT TO SPEECH
    // ---------------------------------------------------------

    fun speak(
        message: String,
        languageTag: String = currentLanguageTag
    ) {

        if (message.isBlank()) return

        setTtsLanguage(languageTag)

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "SmartSightSpeech"
        )
    }

    private fun setTtsLanguage(languageTag: String) {

        currentLanguageTag = languageTag

        val locale = try {
            Locale.forLanguageTag(languageTag)
        } catch (e: Exception) {
            Locale.US
        }

        val result = textToSpeech?.setLanguage(locale)

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            textToSpeech?.setLanguage(Locale.US)
        }
    }

    // ---------------------------------------------------------
    // NORMAL MICROPHONE MODE
    // ---------------------------------------------------------

    fun startListening() {

        handsFreeMode = false

        startRecognizer()
    }

    // ---------------------------------------------------------
    // HANDS-FREE MODE
    // ---------------------------------------------------------

    fun startHandsFree() {

        handsFreeMode = true

        startRecognizer()
    }

    fun stopHandsFree() {

        handsFreeMode = false

        stopListening()
    }

    // ---------------------------------------------------------
    // SPEECH RECOGNIZER
    // ---------------------------------------------------------

    private fun startRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {

            speak(
                "Speech recognition is not available on this phone.",
                "en-IN"
            )

            return
        }

        stopRecognizerOnly()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(

            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    isListening = true

                    onListeningChanged?.invoke(true)
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {

                    isListening = false

                    onListeningChanged?.invoke(false)
                }

                override fun onError(
                    error: Int
                ) {

                    isListening = false

                    onListeningChanged?.invoke(false)

                    if (handsFreeMode) {

                        restartHandsFree()
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val spokenText =
                        matches?.firstOrNull()

                    val detectedLanguage =
                        detectLanguage(results)

                    if (!spokenText.isNullOrBlank()) {

                        currentLanguageTag =
                            detectedLanguage

                        onSpeechResult?.invoke(
                            spokenText,
                            detectedLanguage
                        )
                    }

                    isListening = false

                    onListeningChanged?.invoke(false)

                    if (handsFreeMode) {

                        restartHandsFree()
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        /*
         * Android 14+ language detection.
         */
        if (Build.VERSION.SDK_INT >= 34) {

            intent.putExtra(
                "android.speech.extra.ENABLE_LANGUAGE_DETECTION",
                true
            )

            intent.putExtra(
                "android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES",
                arrayListOf(
                    "en-IN",
                    "ta-IN",
                    "te-IN",
                    "hi-IN",
                    "ml-IN",
                    "kn-IN"
                )
            )
        }

        /*
         * English is used as the starting language.
         * Android can detect the actual spoken language.
         */
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "en-IN"
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
            "en-IN"
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            false
        )

        speechRecognizer?.startListening(intent)
    }

    // ---------------------------------------------------------
    // LANGUAGE DETECTION
    // ---------------------------------------------------------

    private fun detectLanguage(
        results: Bundle?
    ): String {

        if (Build.VERSION.SDK_INT >= 34) {

            val detectedLanguage =
                results?.getString(
                    "android.speech.extra.DETECTED_LANGUAGE"
                )

            if (!detectedLanguage.isNullOrBlank()) {

                return detectedLanguage
            }
        }

        return currentLanguageTag
    }

    // ---------------------------------------------------------
    // RESTART HANDS-FREE LISTENING
    // ---------------------------------------------------------

    private fun restartHandsFree() {

        if (!handsFreeMode) return

        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            if (handsFreeMode) {

                startRecognizer()
            }

        }, 700)
    }

    // ---------------------------------------------------------
    // STOP LISTENING
    // ---------------------------------------------------------

    fun stopListening() {

        handsFreeMode = false

        stopRecognizerOnly()

        isListening = false

        onListeningChanged?.invoke(false)
    }

    private fun stopRecognizerOnly() {

        try {

            speechRecognizer?.stopListening()

            speechRecognizer?.cancel()

            speechRecognizer?.destroy()

        } catch (e: Exception) {

            e.printStackTrace()
        }

        speechRecognizer = null
    }

    // ---------------------------------------------------------
    // CURRENT LANGUAGE
    // ---------------------------------------------------------

    fun getCurrentLanguage(): String {

        return currentLanguageTag
    }

    // ---------------------------------------------------------
    // RELEASE RESOURCES
    // ---------------------------------------------------------

    fun release() {

        handsFreeMode = false

        stopRecognizerOnly()

        textToSpeech?.stop()

        textToSpeech?.shutdown()

        textToSpeech = null
    }
}