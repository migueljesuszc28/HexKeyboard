package com.example.hexkeyboard.service

import android.content.Context
import android.util.Log
import android.view.textservice.*
import com.example.hexkeyboard.logic.engine.PredictionEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpellCheckerManager(
    context: Context,
    private val predictionEngine: PredictionEngine
) : SpellCheckerSession.SpellCheckerSessionListener {

    private val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as TextServicesManager
    private var session: SpellCheckerSession? = null

    private val _suggestionsState = MutableStateFlow<List<String>>(emptyList())
    val suggestionsState: StateFlow<List<String>> = _suggestionsState.asStateFlow()

    private var lastFetchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun initSession(locale: Locale? = null) {
        if (session == null) {
            session = tsm.newSpellCheckerSession(null, locale, this, true)
        }
    }

    fun closeSession() {
        lastFetchJob?.cancel()
        session?.close()
        session = null
    }

    fun clearSuggestions() {
        lastFetchJob?.cancel()
        _suggestionsState.value = emptyList()
    }

    /**
     * Obtiene sugerencias combinando el sistema de Android y nuestro motor interno.
     * Implementa debouncing para ahorrar RAM y evitar saturar el Binder.
     */
    fun fetchSuggestions(text: String) {
        if (text.isBlank()) {
            clearSuggestions()
            return
        }

        lastFetchJob?.cancel()
        lastFetchJob = scope.launch {
            delay(150) // Debounce de 150ms estilo Gboard
            
            // 1. Obtener sugerencias de nuestro motor interno (en hilo de fondo)
            val internalSuggestions = withContext(Dispatchers.Default) {
                predictionEngine.getSuggestions(text)
                    .filter { it.isCorrection }
                    .map { it.text }
            }

            // 2. Intentar obtener sugerencias del sistema Android
            if (session == null) initSession()
            val currentSession = session
            if (currentSession != null) {
                try {
                    currentSession.getSuggestions(TextInfo(text), 3) // Pedimos menos al sistema para ahorrar RAM
                } catch (e: Exception) {
                    Log.e("HexKB", "Error fetching system suggestions", e)
                    _suggestionsState.value = internalSuggestions.distinct()
                }
            } else {
                _suggestionsState.value = internalSuggestions.distinct()
            }
        }
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        if (results == null) return
        
        val systemList = results.flatMap { info ->
            (0 until info.suggestionsCount).mapNotNull { i ->
                info.getSuggestionAt(i).takeIf { it.isNotEmpty() }
            }
        }

        // Combinamos los resultados del sistema con los de nuestro motor local
        val combined = (systemList + _suggestionsState.value).distinct()
        _suggestionsState.value = combined
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        val list = mutableListOf<String>()
        results?.forEach { sentenceInfo ->
            for (i in 0 until sentenceInfo.suggestionsCount) {
                val info = sentenceInfo.getSuggestionsInfoAt(i)
                for (j in 0 until info.suggestionsCount) {
                    val suggestion = info.getSuggestionAt(j)
                    if (suggestion.isNotEmpty()) list.add(suggestion)
                }
            }
        }
        if (list.isNotEmpty()) {
            _suggestionsState.value = list.distinct()
        }
    }
}
