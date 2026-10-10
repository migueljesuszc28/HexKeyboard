package com.example.hexkeyboard.logic.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de aprendizaje de patrones de escritura N-Gramas (Trigramas + Bigramas).
 * Permite al teclado anticipar la siguiente palabra basándose en el historial
 * reciente del usuario y persiste los patrones aprendidos asíncronamente en disco.
 */
class PatternLearningManager(private val context: Context) {

    companion object {
        private const val TAG = "PatternLearningManager"
        private const val FILE_NAME = "learned_ngrams.json"
        private const val MAX_TRIGRAMS = 1200
        private const val MAX_BIGRAMS = 800
        private const val SAVE_DEBOUNCE_MS = 2000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var saveJob: Job? = null

    // Trigramas: "w1_w2" -> { "w3" -> Frecuencia }
    private val trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Bigramas: "w1" -> { "w2" -> Frecuencia }
    private val bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (!file.exists()) {
                    loadDefaultPatterns()
                    return@launch
                }

                val jsonStr = file.readText()
                if (jsonStr.isBlank()) {
                    loadDefaultPatterns()
                    return@launch
                }

                val root = JSONObject(jsonStr)

                if (root.has("bigrams")) {
                    val bgObj = root.getJSONObject("bigrams")
                    bgObj.keys().forEach { key ->
                        val subObj = bgObj.getJSONObject(key)
                        val innerMap = ConcurrentHashMap<String, Int>()
                        subObj.keys().forEach { subKey ->
                            innerMap[subKey] = subObj.getInt(subKey)
                        }
                        bigrams[key] = innerMap
                    }
                }

                if (root.has("trigrams")) {
                    val tgObj = root.getJSONObject("trigrams")
                    tgObj.keys().forEach { key ->
                        val subObj = tgObj.getJSONObject(key)
                        val innerMap = ConcurrentHashMap<String, Int>()
                        subObj.keys().forEach { subKey ->
                            innerMap[subKey] = subObj.getInt(subKey)
                        }
                        trigrams[key] = innerMap
                    }
                }

                if (bigrams.isEmpty()) {
                    loadDefaultPatterns()
                }

                Log.d(TAG, "Patrones cargados de disco: ${bigrams.size} bigramas, ${trigrams.size} trigramas")
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando patrones de disco: ${e.message}")
                loadDefaultPatterns()
            }
        }
    }

    private fun loadDefaultPatterns() {
        addBigramInternal("muchas", "gracias", 10)
        addBigramInternal("buenos", "días", 10)
        addBigramInternal("buenas", "noches", 10)
        addBigramInternal("hola", "cómo", 8)
        addBigramInternal("cómo", "estás", 8)
        addBigramInternal("estoy", "bien", 8)
        addBigramInternal("por", "favor", 10)
        addBigramInternal("de", "nada", 10)
        addBigramInternal("nos", "vemos", 8)
        addBigramInternal("hasta", "luego", 8)

        // Semilla de contexto diacrítico (el vs él)
        addBigramInternal("en", "el", 12)
        addBigramInternal("para", "el", 12)
        addBigramInternal("por", "el", 12)
        addBigramInternal("con", "él", 15)
        addBigramInternal("para", "él", 15)
        addBigramInternal("sin", "él", 15)
        addBigramInternal("él", "dijo", 12)
        addBigramInternal("él", "es", 12)

        // Semilla de contexto diacrítico (esta vs está)
        addBigramInternal("esta", "casa", 12)
        addBigramInternal("esta", "tarde", 12)
        addBigramInternal("esta", "noche", 12)
        addBigramInternal("esta", "semana", 12)
        addBigramInternal("está", "bien", 15)
        addBigramInternal("está", "listo", 15)
        addBigramInternal("ya", "está", "15".toIntOrNull() ?: 15)

        // Semilla de contexto diacrítico (si vs sí)
        addBigramInternal("si", "vienes", 12)
        addBigramInternal("si", "quieres", 12)
        addBigramInternal("por", "si", 12)
        addBigramInternal("que", "sí", 15)
        addBigramInternal("sí", "quiero", 15)

        // Semilla de contexto diacrítico (tu vs tú)
        addBigramInternal("tu", "casa", 12)
        addBigramInternal("tu", "amigo", 12)
        addBigramInternal("tú", "eres", 15)
        addBigramInternal("tú", "sabes", 15)

        addTrigramInternal("hola", "cómo", "estás", 8)
        addTrigramInternal("espero", "que", "estés", 8)
        addTrigramInternal("que", "estés", "bien", 8)
        addTrigramInternal("que", "está", "bien", 12)
        addTrigramInternal("dijo", "que", "sí", 15)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveToDisk()
        }
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()

            val bgObj = JSONObject()
            bigrams.forEach { (key, innerMap) ->
                val subObj = JSONObject()
                innerMap.forEach { (k, v) -> subObj.put(k, v) }
                bgObj.put(key, subObj)
            }
            root.put("bigrams", bgObj)

            val tgObj = JSONObject()
            trigrams.forEach { (key, innerMap) ->
                val subObj = JSONObject()
                innerMap.forEach { (k, v) -> subObj.put(k, v) }
                tgObj.put(key, subObj)
            }
            root.put("trigrams", tgObj)

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(root.toString())
            Log.d(TAG, "Patrones guardados exitosamente en disco")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando patrones en disco: ${e.message}")
        }
    }

    private fun addBigramInternal(w1: String, w2: String, weight: Int = 1) {
        val low1 = w1.lowercase()
        val low2 = w2.lowercase()
        val innerMap = bigrams.getOrPut(low1) { ConcurrentHashMap() }
        innerMap[low2] = (innerMap[low2] ?: 0) + weight

        if (bigrams.size > MAX_BIGRAMS) {
            bigrams.remove(bigrams.keys.first())
        }
    }

    private fun addTrigramInternal(w1: String, w2: String, w3: String, weight: Int = 1) {
        val key = "${w1.lowercase()}_${w2.lowercase()}"
        val low3 = w3.lowercase()
        val innerMap = trigrams.getOrPut(key) { ConcurrentHashMap() }
        innerMap[low3] = (innerMap[low3] ?: 0) + weight

        if (trigrams.size > MAX_TRIGRAMS) {
            trigrams.remove(trigrams.keys.first())
        }
    }

    /**
     * Registra una secuencia de escritura (hasta 3 palabras consecutivas) para
     * alimentar los trigramas y bigramas.
     */
    fun recordSequence(w1: String?, w2: String?, w3: String) {
        val clean3 = WordSanitizer.sanitizeToken(w3).cleanWord.lowercase()
        if (clean3.length < 2 || clean3.any { !it.isLetter() }) return

        val clean2 = w2?.let { WordSanitizer.sanitizeToken(it).cleanWord.lowercase() }
        val clean1 = w1?.let { WordSanitizer.sanitizeToken(it).cleanWord.lowercase() }

        if (!clean2.isNullOrEmpty() && clean2.length >= 2 && clean2.all { it.isLetter() }) {
            addBigramInternal(clean2, clean3, 1)

            if (!clean1.isNullOrEmpty() && clean1.length >= 2 && clean1.all { it.isLetter() }) {
                addTrigramInternal(clean1, clean2, clean3, 1)
            }
            scheduleSave()
        }
    }

    /**
     * Devuelve las predicciones de la siguiente palabra evaluando primero trigramas
     * y luego bigramas para lograr alta tasa de acierto.
     */
    fun predictNextWords(prev2: String?, prev1: String?, limit: Int = 5): List<Pair<String, Double>> {
        val results = mutableMapOf<String, Double>()

        val clean1 = prev2?.let { WordSanitizer.sanitizeToken(it).cleanWord.lowercase() }
        val clean2 = prev1?.let { WordSanitizer.sanitizeToken(it).cleanWord.lowercase() }

        // 1. Trigramas (máxima prioridad)
        if (!clean1.isNullOrEmpty() && !clean2.isNullOrEmpty()) {
            val triKey = "${clean1}_$clean2"
            trigrams[triKey]?.entries?.forEach { (nextWord, freq) ->
                results[nextWord] = freq.toDouble() * 150.0 // Ponderación alta para trigrama
            }
        }

        // 2. Bigramas (complementarios o fallback)
        if (!clean2.isNullOrEmpty()) {
            bigrams[clean2]?.entries?.forEach { (nextWord, freq) ->
                val currentScore = results[nextWord] ?: 0.0
                results[nextWord] = currentScore + (freq.toDouble() * 100.0)
            }
        }

        return results.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Pair(it.key, it.value) }
    }

    /**
     * Calcula la puntuación del modelo de lenguaje (Backoff N-grama: Trigrama -> Bigrama -> Unigrama)
     * para una palabra dada en el contexto de hasta dos palabras anteriores.
     */
    fun calculateContextScore(word: String, prev1: String?, prev2: String?): Double {
        val cleanWord = word.lowercase()
        val c1 = prev1?.let { WordSanitizer.sanitizeToken(it).cleanWord.lowercase() }
        val c2 = prev2?.let { WordSanitizer.sanitizeToken(it).cleanWord.lowercase() }

        var score = 1.0

        if (!c2.isNullOrEmpty() && !c1.isNullOrEmpty()) {
            val triKey = "${c2}_$c1"
            val triCount = trigrams[triKey]?.get(cleanWord) ?: 0
            if (triCount > 0) {
                score += triCount * 3.5
            }
        }

        if (!c1.isNullOrEmpty()) {
            val biCount = bigrams[c1]?.get(cleanWord) ?: 0
            if (biCount > 0) {
                score += biCount * 2.0
            }
        }

        return score
    }
}
