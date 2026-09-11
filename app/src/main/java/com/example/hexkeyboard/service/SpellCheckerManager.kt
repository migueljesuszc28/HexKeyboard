package com.example.hexkeyboard.service

import android.util.Log
import com.example.hexkeyboard.logic.engine.PredictionEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpellCheckerManager(
    private val predictionEngine: PredictionEngine
) {

    private val _suggestionsState = MutableStateFlow<List<String>>(emptyList())
    val suggestionsState: StateFlow<List<String>> = _suggestionsState.asStateFlow()

    private var lastFetchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun initSession() {
        // No-op: eliminada dependencia del sistema
    }

    fun closeSession() {
        lastFetchJob?.cancel()
    }

    fun clearSuggestions() {
        lastFetchJob?.cancel()
        _suggestionsState.value = emptyList()
    }

    /**
     * Obtiene sugerencias utilizando únicamente nuestro motor interno.
     * Implementa debouncing para ahorrar RAM y evitar cálculos excesivos.
     */
    fun fetchSuggestions(text: String) {
        if (text.isBlank()) {
            clearSuggestions()
            return
        }

        lastFetchJob?.cancel()
        lastFetchJob = scope.launch {
            delay(150) // Debounce de 150ms estilo Gboard
            
            // Obtener sugerencias de nuestro motor interno (en hilo de fondo)
            val internalSuggestions = withContext(Dispatchers.Default) {
                predictionEngine.getSuggestions(text)
                    .filter { it.isCorrection }
                    .map { it.text }
            }

            _suggestionsState.value = internalSuggestions.distinct()
        }
    }
}
