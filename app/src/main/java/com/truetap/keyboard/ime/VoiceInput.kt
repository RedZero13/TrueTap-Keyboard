package com.truetap.keyboard.ime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous dictation on top of the system speech recogniser: keeps listening after each
 * phrase until [stop] is called or the user goes quiet.
 */
class VoiceInput(
    private val context: Context,
    private val languageTag: String,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onEnd: () -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var heardSomething = false

    fun start() {
        active = true
        listen()
    }

    private fun listen() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStatus("Listening…")
            override fun onBeginningOfSpeech() { heardSomething = true }
            override fun onRmsChanged(rmsdB: Float) = onLevel(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onStatus("Processing…")
            override fun onError(error: Int) {
                val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (active && quiet && heardSomething) {
                    // A pause after speaking: keep dictating.
                    heardSomething = false
                    listen()
                    return
                }
                onStatus(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No connection"
                        else -> if (quiet) "Didn't catch that" else "Voice typing stopped"
                    },
                )
                finish()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onFinal(text)
                if (active) listen() else finish()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        r.startListening(intent)
    }

    fun stop() {
        active = false
        recognizer?.stopListening()
    }

    private fun finish() {
        active = false
        recognizer?.destroy()
        recognizer = null
        onEnd()
    }

    fun cancel() {
        active = false
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        fun available(context: Context) = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
