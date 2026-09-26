package com.example.hexkeyboard.logic.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceRecognitionHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult = _partialResult.asStateFlow()

    interface VoiceResultListener {
        fun onVoiceResult(text: String)
        fun onVoiceError(error: Int)
    }

    private var resultListener: VoiceResultListener? = null

    fun startListening(listener: VoiceResultListener) {
        Log.d("HexKB", "VoiceRecognitionHelper: startListening called")
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("HexKB", "VoiceRecognitionHelper: Speech recognition not available")
            listener.onVoiceError(-1)
            return
        }

        stopListening()

        resultListener = listener
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(intent)
        _isListening.value = true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
        _partialResult.value = ""
    }

    fun destroy() {
        stopListening()
        resultListener = null
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            Log.d("HexKB", "VoiceRecognitionHelper: onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d("HexKB", "VoiceRecognitionHelper: onEndOfSpeech")
            _isListening.value = false
        }

        override fun onError(error: Int) {
            Log.e("HexKB", "VoiceRecognitionHelper: onError: $error")
            _isListening.value = false
            resultListener?.onVoiceError(error)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                resultListener?.onVoiceResult(matches[0])
            }
            stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                _partialResult.value = matches[0]
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}