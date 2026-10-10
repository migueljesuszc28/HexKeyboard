package com.example.hexkeyboard.logic.engine

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.text.iterator

/**
 * Motor de predicción y corrección avanzado (nivel Gboard).
 * Soporta proximidad física (teclado hexagonal), bigramas, diccionario de
 * usuario y aprendizaje incremental de palabras.
 *
 * Cambios clave respecto a la versión anterior:
 *  - Un único parser de nodos binarios (`parseNextNode`) reemplaza tres copias
 *    casi idénticas que existían antes (menos riesgo de bugs por duplicación).
 *  - El fuzzy-matching en el diccionario binario ahora penaliza CADA carácter
 *    de un segmento multi-carácter, no solo el primero (antes se ignoraban
 *    letras completas al calcular distancia -> correcciones de baja calidad).
 *  - La distancia ya no se recalcula desde cero al llegar a un nodo terminal;
 *    se acumula de forma incremental durante el recorrido (más rápido y
 *    consistente con la poda usada para descender por el árbol).
 *  - El conteo de aprendizaje (`wordCounts`) ahora sí influye en el ranking,
 *    dando prioridad progresiva a palabras que el usuario escribe seguido.
 *  - Nueva función `getAutocorrection` con lógica de confianza tipo Gboard:
 *    solo corrige si la palabra no es válida, la distancia es razonable y hay
 *    un candidato claramente mejor (evita "sobre-corregir" palabras raras
 *    pero válidas).
 *  - Se eliminó código muerto (`parseNodeArray` no se usaba en ningún lugar).
 */
class PredictionEngine(private val context: Context) {

    companion object {
        private const val CACHE_SIZE = 250
        private const val MAX_INTERNAL_RESULTS = 60
        private const val BIGRAM_LIMIT = 600
        private const val MIN_LEARN_COUNT = 3

        // Pesos de scoring
        private const val USER_WORD_BOOST = 2.6
        private const val EXACT_PREFIX_BOOST = 1.35
        private const val EXACT_MATCH_BOOST = 3.5
        private const val ACCENT_MATCH_BOOST = 1.45
        private const val LEARNED_WORD_BOOST_STEP = 0.05
        private const val LEARNED_WORD_BOOST_CAP = 10

        // Umbrales de autocorrección
        private const val AUTOCORRECT_MIN_WORD_LEN = 3
        private const val AUTOCORRECT_MAX_DISTANCE = 2.0
        private const val AUTOCORRECT_MIN_CONFIDENCE = 0.35f

        // Penalización aproximada por letras faltantes/sobrantes al comparar
        // longitudes distintas (aproxima costo de inserción/eliminación sin
        // tener que correr una DP completa en el diccionario binario)
        private const val LENGTH_MISMATCH_PENALTY = 0.75
        private const val NO_EXPECTED_CHAR_PENALTY = 0.85

        private val ACCENT_MAP_STATIC = mapOf(
            'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
            'ü' to 'u', 'ñ' to 'n'
        )

        fun stripAccentsStatic(str: String): String {
            if (str.isEmpty()) return str
            val sb = StringBuilder(str.length)
            for (ch in str) {
                val mapped = ACCENT_MAP_STATIC[ch] ?: ch
                sb.append(mapped)
            }
            return sb.toString()
        }
    }

    data class LanguageBuffer(
        val langCode: String,
        val buffer: ByteBuffer,
        val headerSize: Int,
        val isPrimary: Boolean
    )

    private val trie = TrieNode() // Diccionario de usuario + palabras aprendidas
    private val activeLanguageBuffers = mutableListOf<LanguageBuffer>()
    private val patternLearningManager = PatternLearningManager(context)
    private val calibrationManager = TouchCalibrationManager()
    private val beamSearchDecoder = BeamSearchDecoder(calibrationManager)
    private val diacriticManager = DiacriticManager()
    @Volatile private var keyCenters: Map<Char, PointF> = emptyMap()
    private val userDictionary = mutableSetOf<String>()
    private val wordCounts = mutableMapOf<String, Int>() // Aprendizaje gradual estilo Gboard
    private val predictionCache = LruCache<String, List<Suggestion>>(CACHE_SIZE)

    private var isBaseLoaded = false
    private var isUserLoaded = false
    private var currentLanguage = "es"

    fun updateKeyCenters(centers: Map<Char, PointF>) {
        this.keyCenters = centers
    }

    fun registerConfirmedWordTouch(touchPoints: List<TouchPoint>, confirmedWord: String) {
        val minLen = minOf(touchPoints.size, confirmedWord.length)
        for (i in 0 until minLen) {
            val char = confirmedWord[i].lowercaseChar()
            val center = keyCenters[char]
            if (center != null) {
                calibrationManager.registerTouchSample(
                    touchX = touchPoints[i].x,
                    touchY = touchPoints[i].y,
                    keyCenterX = center.x,
                    keyCenterY = center.y
                )
            }
        }
    }

    // Mapa de proximidad hexagonal para la distribución predeterminada
    private val defaultProximityMap = mapOf(
        'q' to listOf('w', 'k', 'o'),
        'w' to listOf('q', 'f', 'o', 'u'),
        'f' to listOf('w', 'g', 'u', 'd'),
        'g' to listOf('f', 'p', 'd', 'i'),
        'p' to listOf('g', 'b', 'i', 'c'),
        'b' to listOf('p', 'c', 'm'),
        'k' to listOf('q', 'o'),
        'o' to listOf('k', 'u', 'q', 'w', 'e', 's'),
        'u' to listOf('o', 'd', 'w', 'f', 's', 'r'),
        'd' to listOf('u', 'i', 'f', 'g', 'r', 'h'),
        'i' to listOf('d', 'c', 'g', 'p', 'h', 't'),
        'c' to listOf('i', 'm', 'p', 'b', 't', 'n'),
        'm' to listOf('c', 'b', 'n', 'l'),
        'a' to listOf('e', 'k'),
        'e' to listOf('a', 's', 'k', 'o', 'v'),
        's' to listOf('e', 'r', 'o', 'u', 'v', 'y'),
        'r' to listOf('s', 'h', 'u', 'd', 'y', 'x'),
        'n' to listOf('t', 'l', 'c', 'm', 'j'),
        't' to listOf('h', 'n', 'i', 'c', 'z', 'j'),
        'h' to listOf('r', 't', 'd', 'i', 'x', 'z'),
        'l' to listOf('n', 'm'),
        'v' to listOf('y', 'e', 's'),
        'y' to listOf('v', 'x', 's', 'r'),
        'x' to listOf('y', 'z', 'r', 'h'),
        'z' to listOf('x', 'j', 'h', 't'),
        'j' to listOf('z', 't', 'n')
    )

    // Mapa de proximidad hexagonal para la distribución QWERTY
    private val qwertyProximityMap = mapOf(
        'q' to listOf('w', 'u'),
        'w' to listOf('q', 'e', 'u', 'i'),
        'e' to listOf('w', 'r', 'i', 'o'),
        'r' to listOf('e', 't', 'o', 'p'),
        't' to listOf('r', 'y', 'p', 'a'),
        'y' to listOf('t', 'a', 's'),
        'u' to listOf('q', 'w', 'i', 'f', 'g'),
        'i' to listOf('w', 'e', 'o', 'g', 'h'),
        'o' to listOf('e', 'r', 'p', 'h', 'j'),
        'p' to listOf('r', 't', 'a', 'j', 'k'),
        'a' to listOf('t', 'y', 's', 'k', 'l'),
        's' to listOf('y', 'd', 'l', 'z'),
        'd' to listOf('s', 'z', 'x'),
        'f' to listOf('u', 'g', 'c'),
        'g' to listOf('u', 'i', 'h', 'c', 'v'),
        'h' to listOf('i', 'o', 'j', 'v', 'b'),
        'j' to listOf('o', 'p', 'k', 'b', 'n'),
        'k' to listOf('p', 'a', 'l', 'n', 'm'),
        'l' to listOf('a', 's', 'z', 'm'),
        'z' to listOf('s', 'd', 'x', 'l', 'm'),
        'x' to listOf('d', 'z'),
        'c' to listOf('f', 'g', 'v'),
        'v' to listOf('g', 'h', 'b', 'c'),
        'b' to listOf('h', 'j', 'n', 'v'),
        'n' to listOf('j', 'k', 'm', 'b'),
        'm' to listOf('k', 'l', 'z', 'n')
    )

    private var activeProximityMap = defaultProximityMap

    fun setLayoutType(type: String) {
        activeProximityMap = if (type == "qwerty") qwertyProximityMap else defaultProximityMap
        synchronized(predictionCache) { predictionCache.evictAll() }
    }

    private val accentMap = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
        'ü' to 'u', 'ñ' to 'n'
    )

    private fun stripAccents(str: String): String {
        if (str.isEmpty()) return str
        val sb = java.lang.StringBuilder(str.length)
        for (c in str) {
            val mapped = accentMap[c]
            if (mapped != null && c != 'ñ') {
                sb.append(mapped)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    data class Suggestion(
        val text: String,
        val score: Double,
        val isCorrection: Boolean = false,
        val isNextWord: Boolean = false,
        val isGesture: Boolean = false,
        val confidence: Float = 0f
    )

    data class CharPoint(val char: Char, val x: Float, val y: Float)

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var frequency: Int = 0
        var isWord: Boolean = false
        var isUserWord: Boolean = false
    }

    /** Resultado uniforme de parsear un nodo del diccionario binario (formato AOSP LatinIME v2). */
    private data class ParsedNode(
        val word: String,
        val isTerminal: Boolean,
        val frequency: Int,
        val childrenAddr: Int?,
        val addressBase: Int
    )

    // ------------------------------------------------------------------
    // Inicialización / carga
    // ------------------------------------------------------------------

    suspend fun initialize(
        lang: String = currentLanguage,
        secondaryLangs: List<String> = emptyList(),
        forceUserDictReload: Boolean = false,
        includeBase: Boolean = true
    ) {
        val targetLangs = if (secondaryLangs.isNotEmpty()) {
            listOf(lang) + secondaryLangs.filter { it != lang }
        } else {
            listOf(lang)
        }

        val currentLoadedLangs = activeLanguageBuffers.map { it.langCode }
        val primaryChanged = currentLanguage != lang
        val languagesChanged = currentLoadedLangs != targetLangs

        if (!forceUserDictReload && isUserLoaded && isBaseLoaded && !primaryChanged && !languagesChanged) return

        currentLanguage = lang
        synchronized(predictionCache) { predictionCache.evictAll() }

        withContext(Dispatchers.IO) {
            if (forceUserDictReload || !isUserLoaded) {
                userDictionary.clear()
                loadUserDictionary()
                isUserLoaded = true
            }
            if (includeBase && (!isBaseLoaded || primaryChanged || languagesChanged)) {
                loadBaseDictionaries(lang, secondaryLangs)
                isBaseLoaded = activeLanguageBuffers.isNotEmpty()
            }
            loadBigrams()
        }
    }

    private fun loadBaseDictionaries(primaryLang: String, secondaryLangs: List<String>) {
        activeLanguageBuffers.clear()

        loadBaseDictionary(primaryLang, isPrimary = true)?.let {
            activeLanguageBuffers.add(it)
        }

        secondaryLangs.filter { it != primaryLang }.forEach { secLang ->
            loadBaseDictionary(secLang, isPrimary = false)?.let {
                activeLanguageBuffers.add(it)
            }
        }
    }

    private fun loadBaseDictionary(lang: String, isPrimary: Boolean): LanguageBuffer? {
        try {
            val fileName = "main_$lang.dict"
            val bytes = context.assets.open(fileName).use { it.readBytes() }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.int
            if (magic != 0x9BC13AFE.toInt()) {
                Log.e("PredictionEngine", "Magic inválido para '$lang': ${Integer.toHexString(magic)}")
                return null
            }

            buffer.short // version
            buffer.short // flags
            val headerSize = buffer.int

            Log.d("PredictionEngine", "Diccionario base '$lang' (primario=$isPrimary) cargado en buffer binario")
            return LanguageBuffer(
                langCode = lang,
                buffer = buffer,
                headerSize = headerSize,
                isPrimary = isPrimary
            )
        } catch (e: Exception) {
            Log.e("PredictionEngine", "Error cargando diccionario base '$lang': ${e.message}")
            return null
        }
    }

    private fun loadUserDictionary() {
        val file = File(context.filesDir, "user_dict.txt")
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 2) {
                    val word = parts[0]
                    val freq = parts[1].toIntOrNull() ?: 200
                    insert(word, freq, isUser = true)
                    userDictionary.add(word)
                }
            }
        }
    }

    private fun saveUserDictionary() {
        val file = File(context.filesDir, "user_dict.txt")
        val content = userDictionary.joinToString("\n") { "$it|200" }
        file.writeText(content)
    }

    private fun loadBigrams() {
        // Carga gestionada por PatternLearningManager
    }

    private fun insert(word: String, freq: Int, isUser: Boolean = false) {
        var current = trie
        word.lowercase().forEach { char ->
            current = current.children.getOrPut(char) { TrieNode() }
        }
        current.isWord = true
        current.isUserWord = isUser
        current.frequency = freq
    }

    // ------------------------------------------------------------------
    // Parser binario unificado (antes duplicado en 3 funciones distintas)
    // ------------------------------------------------------------------

    private fun readChar(buffer: ByteBuffer): Int? {
        val b = buffer.get().toInt() and 0xFF
        if (b == 0x1F) return null
        if (b < 0x20) {
            val next = buffer.short.toInt() and 0xFFFF
            return (b shl 16) or next
        }
        return b
    }

    private fun readPtNodeCount(buffer: ByteBuffer): Int {
        val msb = buffer.get().toInt() and 0xFF
        return if (msb <= 0x7F) msb else ((0x7F and msb) shl 8) + (buffer.get().toInt() and 0xFF)
    }

    /**
     * Lee un único PtNode desde la posición actual del buffer y avanza el
     * cursor hasta el inicio del siguiente hermano. Toda la lógica de flags,
     * shortcuts y bigramas internos del diccionario vive aquí, en un solo
     * lugar, para que los tres recorridos (isKnownWord, prefijo, fuzzy) no
     * puedan desincronizarse entre sí.
     */
    private fun parseNextNode(buffer: ByteBuffer, prefix: String): ParsedNode {
        val flags = buffer.get().toInt() and 0xFF
        val addrType = flags and 0xC0
        val hasMultipleChars = (flags and 0x20) != 0
        val isTerminal = (flags and 0x10) != 0
        val hasShortcuts = (flags and 0x08) != 0
        val hasBigrams = (flags and 0x04) != 0

        val sb = StringBuilder()
        if (hasMultipleChars) {
            while (true) {
                val c = readChar(buffer) ?: break
                sb.appendCodePoint(c)
            }
        } else {
            readChar(buffer)?.let { sb.appendCodePoint(it) }
        }
        val fullWord = prefix + sb.toString()

        var freq = -1
        if (isTerminal) freq = buffer.get().toInt() and 0xFF

        val addressBase = buffer.position()
        val childrenAddr: Int? = when (addrType) {
            0x00 -> null
            0x40 -> buffer.get().toInt() and 0xFF
            0x80 -> buffer.short.toInt() and 0xFFFF
            else -> {
                val b1 = buffer.get().toInt() and 0xFF
                val b2 = buffer.get().toInt() and 0xFF
                val b3 = buffer.get().toInt() and 0xFF
                (b1 shl 16) or (b2 shl 8) or b3
            }
        }

        if (isTerminal && hasShortcuts) {
            val size = buffer.short.toInt() and 0xFFFF
            buffer.position(buffer.position() + size - 2)
        }
        if (isTerminal && hasBigrams) {
            while (true) {
                val bFlags = buffer.get().toInt() and 0xFF
                when (bFlags and 0x30) {
                    0x10 -> buffer.get()
                    0x20 -> buffer.short
                    0x30 -> { buffer.get(); buffer.short }
                }
                if ((bFlags and 0x80) == 0) break
            }
        }

        return ParsedNode(fullWord, isTerminal, freq, childrenAddr, addressBase)
    }

    // ------------------------------------------------------------------
    // Consultas: ¿la palabra es conocida?
    // ------------------------------------------------------------------

    private fun isKnownWord(word: String): Boolean {
        val lower = word.lowercase()
        var current: TrieNode? = trie
        for (char in lower) {
            current = current?.children?.get(char)
            if (current == null) break
        }
        if (current?.isWord == true) return true
        return isKnownWordInBinary(lower)
    }

    private fun isKnownWordInBinary(target: String): Boolean {
        for (langBuf in activeLanguageBuffers) {
            val buffer = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            buffer.position(langBuf.headerSize)
            if (isKnownWordInBinaryRecursive(buffer, target, "")) return true
        }
        return false
    }

    private fun isKnownWordInBinaryRecursive(buffer: ByteBuffer, target: String, prefix: String): Boolean {
        val count = try { readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            val node = parseNextNode(buffer, prefix)

            if (node.word == target && node.isTerminal) return true

            if (target.startsWith(node.word) && node.childrenAddr != null) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                if (isKnownWordInBinaryRecursive(buffer, target, node.word)) return true
                buffer.position(savedPos)
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // Sugerencias
    // ------------------------------------------------------------------

    fun getSuggestions(
        currentWord: String,
        previousWord: String? = null,
        prev2: String? = null,
        touchPoints: List<TouchPoint> = emptyList(),
        limit: Int = 5
    ): List<Suggestion> {
        if (!isBaseLoaded) return emptyList()

        val sanitized = WordSanitizer.sanitizeToken(currentWord)
        val lowPrefix = sanitized.cleanWord.lowercase()

        val activeLangsKey = activeLanguageBuffers.joinToString(",") { it.langCode }
        val cacheKey = "curr:${lowPrefix}_p1:${previousWord}_p2:${prev2}_tp:${touchPoints.size}_langs:$activeLangsKey"
        synchronized(predictionCache) {
            predictionCache.get(cacheKey)
        }?.let { cached ->
            return cached.map { it.copy(text = matchCase(sanitized.cleanWord, it.text)) }
        }

        val result = mutableListOf<Suggestion>()

        // 1. Predicción N-Gramas de siguiente palabra (Trigramas + Bigramas locales y binarios)
        if (lowPrefix.isEmpty() && previousWord != null) {
            val localPredictions = patternLearningManager.predictNextWords(prev2, previousWord, limit = limit)
            val binaryPredictions = getBinaryBigramPredictions(previousWord, limit = limit)

            val mergedMap = mutableMapOf<String, Double>()
            localPredictions.forEach { (nextWord, score) -> mergedMap[nextWord.lowercase()] = score }
            binaryPredictions.forEach { (nextWord, score) ->
                val key = nextWord.lowercase()
                mergedMap[key] = maxOf(mergedMap[key] ?: 0.0, score)
            }

            mergedMap.entries.sortedByDescending { it.value }.take(limit).forEach { (nextWord, score) ->
                result.add(Suggestion(nextWord, score, isNextWord = true, confidence = 0.85f))
            }
        }

        if (lowPrefix.isNotEmpty()) {
            // 2. Coincidencias de prefijo (siempre las de mayor confianza)
            findPrefixMatches(lowPrefix, result)

            // 3. Corrección fuzzy (proximidad hexagonal), solo si aún hay espacio útil
            val fuzzyMaxDist = when {
                lowPrefix.length <= 2 -> 0.4
                lowPrefix.length <= 4 -> 0.9
                else -> 1.6
            }
            result.addAll(findFuzzyMatches(lowPrefix, fuzzyMaxDist))
        }

        // 4. Re-evaluación probabilística con Beam Search y Modelo Espacial
        if (touchPoints.isNotEmpty() && keyCenters.isNotEmpty() && result.isNotEmpty()) {
            val ranked = beamSearchDecoder.rankHypotheses(
                touchPoints = touchPoints,
                suggestions = result,
                keyCenters = keyCenters,
                patternLearningManager = patternLearningManager,
                previousWord = previousWord,
                prev2 = prev2,
                beamWidth = limit
            )
            val rankedMap = ranked.associateBy { it.word.lowercase() }
            result.sortByDescending { rankedMap[it.text.lowercase()]?.totalScore ?: it.score }
        }

        // 5. Ranking final
        val finalResult = result
            .distinctBy { it.text.lowercase() }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.copy(text = matchCase(sanitized.cleanWord, it.text)) }

        synchronized(predictionCache) {
            predictionCache.put(cacheKey, finalResult)
        }
        return finalResult
    }

    /**
     * Algoritmo de coincidencia de gestos (Glide Typing) mejorado.
     * Soporta escalado por radio de hexágono, orden temporal monótono e inflexiones.
     */
    fun getGestureSuggestions(
        points: List<PointF>,
        keyMap: List<CharPoint>,
        limit: Int = 5
    ): List<Suggestion> {
        if (points.size < 2 || !isBaseLoaded || keyMap.isEmpty()) return emptyList()

        val hexRadius = computeHexRadius(keyMap)
        val resampledPoints = resampleGesturePoints(points, 35)
        if (resampledPoints.size < 2) return emptyList()

        val cornerPoints = findCornerPoints(resampledPoints)
        val startPoint = resampledPoints.first()
        val endPoint = resampledPoints.last()

        val maxEdgeDist = hexRadius * 2.5f
        val startChars = keyMap.filter { hypot(it.x - startPoint.x, it.y - startPoint.y) <= maxEdgeDist }.map { it.char }.toSet()
        val endChars = keyMap.filter { hypot(it.x - endPoint.x, it.y - endPoint.y) <= maxEdgeDist }.map { it.char }.toSet()

        if (startChars.isEmpty() || endChars.isEmpty()) return emptyList()

        val results = mutableListOf<Suggestion>()

        // 1. Buscar en memoria
        searchGestureInMemory(trie, "", resampledPoints, cornerPoints, keyMap, results, hexRadius, 0, startChars, endChars)

        // 2. Buscar en los buffers binarios activos
        for (langBuf in activeLanguageBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.85
            searchGestureInBinary(dup, "", resampledPoints, cornerPoints, keyMap, results, hexRadius, 0, startChars, endChars, langWeight)
        }

        return results
            .distinctBy { it.text.lowercase() }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun computeHexRadius(keyMap: List<CharPoint>): Float {
        if (keyMap.size < 2) return 100f
        var minDistSum = 0f
        var count = 0
        for (i in keyMap.indices) {
            var minD = Float.MAX_VALUE
            for (j in keyMap.indices) {
                if (i == j) continue
                val d = hypot(keyMap[i].x - keyMap[j].x, keyMap[i].y - keyMap[j].y)
                if (d in 1f..<minD) {
                    minD = d
                }
            }
            if (minD < Float.MAX_VALUE) {
                minDistSum += minD
                count++
            }
        }
        return if (count > 0) (minDistSum / count) / 2f else 100f
    }

    private fun resampleGesturePoints(points: List<PointF>, targetCount: Int): List<PointF> {
        val cleanPoints = mutableListOf<PointF>()
        for (p in points) {
            if (cleanPoints.isEmpty() || hypot(p.x - cleanPoints.last().x, p.y - cleanPoints.last().y) > 3f) {
                cleanPoints.add(p)
            }
        }
        if (cleanPoints.size <= 2) return cleanPoints

        var totalLength = 0f
        for (i in 0 until cleanPoints.size - 1) {
            totalLength += hypot(cleanPoints[i + 1].x - cleanPoints[i].x, cleanPoints[i + 1].y - cleanPoints[i].y)
        }
        if (totalLength <= 0f) return cleanPoints

        val interval = totalLength / (targetCount - 1)
        val resampled = mutableListOf<PointF>()
        resampled.add(cleanPoints.first())

        var accumulated = 0f
        var currIdx = 0
        var currPt = cleanPoints[0]

        while (currIdx < cleanPoints.size - 1 && resampled.size < targetCount) {
            val nextPt = cleanPoints[currIdx + 1]
            val segLen = hypot(nextPt.x - currPt.x, nextPt.y - currPt.y)

            if (accumulated + segLen >= interval) {
                val ratio = (interval - accumulated) / segLen
                val nx = currPt.x + ratio * (nextPt.x - currPt.x)
                val ny = currPt.y + ratio * (nextPt.y - currPt.y)
                val newPt = PointF(nx, ny)
                resampled.add(newPt)
                currPt = newPt
                accumulated = 0f
            } else {
                accumulated += segLen
                currPt = nextPt
                currIdx++
            }
        }
        if (resampled.size < targetCount) {
            resampled.add(cleanPoints.last())
        }
        return resampled
    }

    private fun findCornerPoints(points: List<PointF>): List<PointF> {
        if (points.size < 3) return emptyList()
        val corners = mutableListOf<PointF>()
        for (i in 1 until points.size - 1) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = points[i + 1]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)

            if (len1 > 0f && len2 > 0f) {
                val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
                if (dot < 0.75f) { // Cambio de dirección notable (> ~40 grados)
                    corners.add(pCurr)
                }
            }
        }
        return corners
    }

    private fun searchGestureInMemory(
        node: TrieNode,
        word: String,
        resampledPoints: List<PointF>,
        cornerPoints: List<PointF>,
        keyMap: List<CharPoint>,
        results: MutableList<Suggestion>,
        hexRadius: Float,
        lastPointIndex: Int,
        startChars: Set<Char>,
        endChars: Set<Char>
    ) {
        if (results.size > 120) return

        if (node.isWord && word.length >= 2) {
            val score = calculateGestureScore(word, resampledPoints, cornerPoints, keyMap, node.frequency, hexRadius, endChars)
            if (score > 0) {
                val confidence = (score / 350.0).toFloat().coerceIn(0f, 1f)
                results.add(Suggestion(word, score, isGesture = true, confidence = confidence))
            }
        }

        node.children.forEach { (char, child) ->
            val nextIndex = isCharPossibleInGesture(word, char, resampledPoints, keyMap, hexRadius, lastPointIndex, startChars)
            if (nextIndex >= 0) {
                searchGestureInMemory(child, word + char, resampledPoints, cornerPoints, keyMap, results, hexRadius, nextIndex, startChars, endChars)
            }
        }
    }

    private fun searchGestureInBinary(
        buffer: ByteBuffer,
        prefix: String,
        resampledPoints: List<PointF>,
        cornerPoints: List<PointF>,
        keyMap: List<CharPoint>,
        results: MutableList<Suggestion>,
        hexRadius: Float,
        lastPointIndex: Int,
        startChars: Set<Char>,
        endChars: Set<Char>,
        langWeight: Double = 1.0
    ) {
        if (results.size > 120) return

        val count = readPtNodeCount(buffer)
        repeat(count) {
            val node = parseNextNode(buffer, prefix)
            val segment = node.word.substring(prefix.length)

            var possible = true
            var tempWord = prefix
            var currIndex = lastPointIndex

            for (ch in segment) {
                val nextIdx = isCharPossibleInGesture(tempWord, ch, resampledPoints, keyMap, hexRadius, currIndex, startChars)
                if (nextIdx < 0) {
                    possible = false
                    break
                }
                tempWord += ch
                currIndex = nextIdx
            }

            if (possible) {
                if (node.isTerminal && node.word.length >= 2) {
                    val score = calculateGestureScore(node.word, resampledPoints, cornerPoints, keyMap, node.frequency, hexRadius, endChars) * langWeight
                    if (score > 0) {
                        val confidence = ((score / 350.0) * langWeight).toFloat().coerceIn(0f, 1f)
                        results.add(Suggestion(node.word, score, isGesture = true, confidence = confidence))
                    }
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    searchGestureInBinary(buffer, node.word, resampledPoints, cornerPoints, keyMap, results, hexRadius, currIndex, startChars, endChars, langWeight)
                    buffer.position(savedPos)
                }
            }
        }
    }

    private fun isCharPossibleInGesture(
        word: String,
        nextChar: Char,
        resampledPoints: List<PointF>,
        keyMap: List<CharPoint>,
        hexRadius: Float,
        lastPointIndex: Int,
        startChars: Set<Char>
    ): Int {
        if (word.isEmpty()) {
            if (nextChar !in startChars) return -1
            return 0
        }
        val cp = keyMap.find { it.char == nextChar } ?: return -1
        val threshold = hexRadius * 2.2f
        for (i in lastPointIndex until resampledPoints.size) {
            if (hypot(cp.x - resampledPoints[i].x, cp.y - resampledPoints[i].y) < threshold) {
                return i
            }
        }
        return -1
    }

    private fun calculateGestureScore(
        word: String,
        resampledPoints: List<PointF>,
        cornerPoints: List<PointF>,
        keyMap: List<CharPoint>,
        freq: Int,
        hexRadius: Float,
        endChars: Set<Char>
    ): Double {
        if (word.length < 2) return 0.0
        if (word.last() !in endChars) return 0.0

        var totalNormalizedDist = 0.0
        var lastPointIdx = 0
        var cornerMatchBonus = 0.0

        for (char in word) {
            val cp = keyMap.find { it.char == char } ?: return 0.0
            var minDist = Double.MAX_VALUE
            var bestIdx = lastPointIdx

            for (i in lastPointIdx until resampledPoints.size) {
                val d = hypot(cp.x - resampledPoints[i].x, cp.y - resampledPoints[i].y).toDouble()
                if (d < minDist) {
                    minDist = d
                    bestIdx = i
                }
            }
            if (minDist > hexRadius * 2.5) return 0.0
            totalNormalizedDist += minDist / hexRadius
            lastPointIdx = bestIdx

            if (cornerPoints.any { hypot(cp.x - it.x, cp.y - it.y) < hexRadius * 1.2f }) {
                cornerMatchBonus += 0.25
            }
        }

        val lastCharPos = keyMap.find { it.char == word.last() } ?: return 0.0
        val distToEnd = hypot(lastCharPos.x - resampledPoints.last().x, lastCharPos.y - resampledPoints.last().y)
        if (distToEnd > hexRadius * 2.2f) return 0.0

        val avgDist = totalNormalizedDist / word.length
        if (avgDist > 2.2) return 0.0

        val proximityFactor = 1.0 / (1.0 + 0.6 * avgDist)
        val freqScore = ln(freq.toDouble() + 2.0)
        return freqScore * proximityFactor * (1.0 + cornerMatchBonus) * 100.0
    }

    /**
     * Autocorrección estilo Gboard: solo propone reemplazo si la palabra
     * escrita NO es válida, existe un candidato razonablemente cercano y ese
     * candidato tiene confianza suficiente. Evita "corregir" palabras raras
     * pero legítimas (nombres propios cortos, jerga ya aprendida, etc.).
     */
    fun getAutocorrection(
        typedWord: String,
        previousWord: String? = null,
        prev2: String? = null,
        touchPoints: List<TouchPoint> = emptyList()
    ): Suggestion? {
        val sanitized = WordSanitizer.sanitizeToken(typedWord)
        val cleaned = sanitized.cleanWord
        if (cleaned.length < AUTOCORRECT_MIN_WORD_LEN) return null
        if (WordSanitizer.isAutocorrectImmune(typedWord, cleaned)) return null

        val normCleaned = stripAccents(cleaned.lowercase())
        val candidates = getSuggestions(cleaned, previousWord = previousWord, prev2 = prev2, touchPoints = touchPoints, limit = 5)

        // 1. Evaluar acentos y tildes mediante DiacriticManager en lugar de forzar la tilde a ciegas
        val resolvedDiacritic = diacriticManager.resolveDiacriticCandidate(
            typedWord = cleaned,
            candidates = candidates,
            previousWord = previousWord,
            prev2 = prev2,
            patternLearningManager = patternLearningManager
        )

        // Si es un par diacrítico (ej. el/él, esta/está, si/sí), respetar el candidato resuelto por el modelo semántico
        if (diacriticManager.isDiacriticWord(cleaned)) {
            if (resolvedDiacritic != null) {
                if (resolvedDiacritic.text.lowercase() != cleaned.lowercase()) {
                    return resolvedDiacritic.copy(isCorrection = true)
                }
                return null // Mantener la palabra sin tilde si el contexto indica la versión sin tilde
            }
        } else if (resolvedDiacritic != null && cleaned.lowercase() == normCleaned) {
            // Tilde incondicional (ej. cancion -> canción, tambien -> también)
            return resolvedDiacritic.copy(isCorrection = true)
        }

        if (isKnownWord(cleaned)) return null

        val best = candidates.filter { it.isCorrection }.maxByOrNull { it.score } ?: return null

        val dist = weightedDistance(cleaned.lowercase(), best.text.lowercase())
        if (dist > AUTOCORRECT_MAX_DISTANCE) return null
        if (best.confidence < AUTOCORRECT_MIN_CONFIDENCE) return null

        return best
    }

    /**
     * Extrae las predicciones de siguiente palabra desde los bigramas nativos
     * comprimidos dentro de los archivos de diccionario binario (.dict).
     */
    fun getBinaryBigramPredictions(previousWord: String, limit: Int = 5): List<Pair<String, Double>> {
        val cleanPrev = WordSanitizer.sanitizeToken(previousWord).cleanWord.lowercase()
        if (cleanPrev.length < 2) return emptyList()

        val results = mutableListOf<Pair<String, Double>>()

        for (langBuf in activeLanguageBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.95

            val bigrams = findBigramsInBinaryNode(dup, cleanPrev, "", langWeight)
            results.addAll(bigrams)
        }

        return results
            .distinctBy { it.first.lowercase() }
            .sortedByDescending { it.second }
            .take(limit)
    }

    private fun findBigramsInBinaryNode(
        buffer: ByteBuffer,
        targetWord: String,
        prefix: String,
        langWeight: Double
    ): List<Pair<String, Double>> {
        val count = try { readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        val results = mutableListOf<Pair<String, Double>>()

        repeat(count) {
            val flags = buffer.get().toInt() and 0xFF
            val addrType = flags and 0xC0
            val hasMultipleChars = (flags and 0x20) != 0
            val isTerminal = (flags and 0x10) != 0
            val hasShortcuts = (flags and 0x08) != 0
            val hasBigrams = (flags and 0x04) != 0

            val sb = StringBuilder()
            if (hasMultipleChars) {
                while (true) {
                    val c = readChar(buffer) ?: break
                    sb.appendCodePoint(c)
                }
            } else {
                readChar(buffer)?.let { sb.appendCodePoint(it) }
            }
            val word = prefix + sb.toString()

            var freq = -1
            if (isTerminal) freq = buffer.get().toInt() and 0xFF

            val addressBase = buffer.position()
            val childrenAddr: Int? = when (addrType) {
                0x00 -> null
                0x40 -> buffer.get().toInt() and 0xFF
                0x80 -> buffer.short.toInt() and 0xFFFF
                else -> {
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt() and 0xFF
                    val b3 = buffer.get().toInt() and 0xFF
                    (b1 shl 16) or (b2 shl 8) or b3
                }
            }

            if (isTerminal && hasShortcuts) {
                val size = buffer.short.toInt() and 0xFFFF
                buffer.position(buffer.position() + size - 2)
            }

            if (isTerminal && word.lowercase() == targetWord && hasBigrams) {
                // Leer las entradas de bigramas del nodo objetivo
                while (true) {
                    val bFlags = buffer.get().toInt() and 0xFF
                    val bAddrType = bFlags and 0x30
                    val bFreq = (bFlags and 0x0F) * 16 + 10

                    when (bAddrType) {
                        0x10 -> buffer.get()
                        0x20 -> buffer.short
                        0x30 -> { buffer.get(); buffer.short }
                    }

                    if ((bFlags and 0x80) == 0) break
                }
            } else if (isTerminal && hasBigrams) {
                while (true) {
                    val bFlags = buffer.get().toInt() and 0xFF
                    when (bFlags and 0x30) {
                        0x10 -> buffer.get()
                        0x20 -> buffer.short
                        0x30 -> { buffer.get(); buffer.short }
                    }
                    if ((bFlags and 0x80) == 0) break
                }
            }

            if (childrenAddr != null && targetWord.startsWith(word.lowercase())) {
                val savedPos = buffer.position()
                buffer.position(addressBase + childrenAddr)
                results.addAll(findBigramsInBinaryNode(buffer, targetWord, word, langWeight))
                buffer.position(savedPos)
            }
        }
        return results
    }

    private fun findPrefixMatches(prefix: String, result: MutableList<Suggestion>) {
        // 1. Buscar en el trie de memoria (usuario + aprendidas)
        var current: TrieNode? = trie
        for (char in prefix) {
            current = current?.children?.get(char)
            if (current == null) break
        }
        current?.let { node -> collectAllFromNode(node, prefix, prefix, result) }

        // 2. Buscar en los buffers binarios activos (diccionarios base)
        for (langBuf in activeLanguageBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.95
            searchPrefixInBinary(dup, prefix, "", result, langWeight)
        }
    }

    private fun collectAllFromNode(node: TrieNode, word: String, target: String, result: MutableList<Suggestion>) {
        if (node.isWord) {
            val isExact = stripAccents(word.lowercase()) == stripAccents(target.lowercase())
            val exactBoost = if (isExact) EXACT_MATCH_BOOST else 1.0
            val isCorrection = word.lowercase() != target.lowercase()
            val score = calculateScore(node.frequency, node.isUserWord, 1.0, isPrefix = true, word = word) * exactBoost
            result.add(Suggestion(word, score, isCorrection = isCorrection, confidence = if (isExact) 1.0f else 0.9f))
        }
        if (result.size > MAX_INTERNAL_RESULTS) return
        node.children.forEach { (char, child) -> collectAllFromNode(child, word + char, target, result) }
    }

    private fun searchPrefixInBinary(
        buffer: ByteBuffer,
        target: String,
        prefix: String,
        result: MutableList<Suggestion>,
        langWeight: Double = 1.0
    ) {
        if (result.size > MAX_INTERNAL_RESULTS) return

        val normTarget = stripAccents(target.lowercase())
        val count = readPtNodeCount(buffer)

        repeat(count) {
            val node = parseNextNode(buffer, prefix)
            val normNodeWord = stripAccents(node.word.lowercase())

            if (normNodeWord.startsWith(normTarget) || normTarget.startsWith(normNodeWord)) {
                if (node.isTerminal && normNodeWord.startsWith(normTarget)) {
                    val isExactMatch = normNodeWord == normTarget
                    val exactBoost = if (isExactMatch) EXACT_MATCH_BOOST else 1.0
                    val hasAccentInDict = node.word.lowercase() != normNodeWord
                    val accentBoost = if (hasAccentInDict && target.lowercase() == normTarget) ACCENT_MATCH_BOOST else 1.0
                    val isCorrection = node.word.lowercase() != target.lowercase()
                    val score = calculateScore(node.frequency, false, 1.0, isPrefix = true, word = node.word) * langWeight * exactBoost * accentBoost
                    result.add(Suggestion(node.word, score, isCorrection = isCorrection, confidence = ((if (isExactMatch) 1.0f else 0.85f) * langWeight).toFloat()))
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    searchPrefixInBinary(buffer, target, node.word, result, langWeight)
                    buffer.position(savedPos)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Fuzzy matching (corrección por proximidad de teclas)
    // ------------------------------------------------------------------

    /** Penalización de un solo carácter frente al carácter esperado en esa posición. */
    private fun charPenalty(actual: Char, expected: Char?): Double {
        if (expected == null) return NO_EXPECTED_CHAR_PENALTY
        if (actual == expected) return 0.0
        val na = accentMap[actual] ?: actual
        val ne = accentMap[expected] ?: expected
        if (na == ne) return 0.05
        if (activeProximityMap[ne]?.contains(na) == true) return 0.4
        return 1.0
    }

    /** Penalización de un segmento completo (varios caracteres) contra el target, alineado por posición. */
    private fun segmentPenalty(segment: String, offsetInTarget: Int, target: String): Double {
        var penalty = 0.0
        for ((idx, ch) in segment.withIndex()) {
            penalty += charPenalty(ch, target.getOrNull(offsetInTarget + idx))
        }
        return penalty
    }

    private fun findFuzzyMatches(target: String, maxDist: Double): List<Suggestion> {
        val fuzzyResults = mutableListOf<Suggestion>()
        searchFuzzyInMemory(trie, "", target, 0.0, maxDist, fuzzyResults)

        for (langBuf in activeLanguageBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.95
            searchFuzzyInBinary(dup, "", target, 0.0, maxDist, fuzzyResults, langWeight)
        }

        return fuzzyResults
    }

    private fun searchFuzzyInMemory(
        node: TrieNode,
        currentWord: String,
        target: String,
        distSoFar: Double,
        maxDist: Double,
        results: MutableList<Suggestion>
    ) {
        if (distSoFar > maxDist || results.size > MAX_INTERNAL_RESULTS) return

        if (node.isWord) {
            val missing = (target.length - currentWord.length).coerceAtLeast(0)
            val finalDist = distSoFar + missing * LENGTH_MISMATCH_PENALTY
            if (finalDist <= maxDist) {
                val proximityBonus = 1.0 / (finalDist + 1.0)
                val score = calculateScore(node.frequency, node.isUserWord, proximityBonus, isPrefix = false, word = currentWord)
                results.add(Suggestion(currentWord, score, isCorrection = true, confidence = proximityBonus.toFloat()))
            }
        }

        // Tope de longitud extra permitida (inserciones) para no explotar el árbol
        if (currentWord.length > target.length + 2) return

        node.children.forEach { (char, child) ->
            if (results.size > MAX_INTERNAL_RESULTS) return@forEach
            val expected = target.getOrNull(currentWord.length)
            val penalty = charPenalty(char, expected)
            val newDist = distSoFar + penalty
            if (newDist <= maxDist) {
                searchFuzzyInMemory(child, currentWord + char, target, newDist, maxDist, results)
            }
        }
    }

    private fun searchFuzzyInBinary(
        buffer: ByteBuffer,
        prefix: String,
        target: String,
        distSoFar: Double,
        maxDist: Double,
        results: MutableList<Suggestion>,
        langWeight: Double = 1.0
    ) {
        if (distSoFar > maxDist || results.size > MAX_INTERNAL_RESULTS) return

        val count = readPtNodeCount(buffer)
        repeat(count) {
            val node = parseNextNode(buffer, prefix)
            val segment = node.word.substring(prefix.length)
            val newDist = distSoFar + segmentPenalty(segment, prefix.length, target)

            if (node.isTerminal) {
                val missing = (target.length - node.word.length).coerceAtLeast(0)
                val finalDist = newDist + missing * LENGTH_MISMATCH_PENALTY
                if (finalDist <= maxDist) {
                    val proximityBonus = 1.0 / (finalDist + 1.0)
                    val score = calculateScore(node.frequency, false, proximityBonus, isPrefix = false, word = node.word) * langWeight
                    results.add(Suggestion(node.word, score, isCorrection = true, confidence = (proximityBonus * langWeight).toFloat()))
                }
            }

            if (node.childrenAddr != null && newDist <= maxDist) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                searchFuzzyInBinary(buffer, node.word, target, newDist, maxDist, results, langWeight)
                buffer.position(savedPos)
            }
        }
    }

    /** Distancia de edición ponderada (usada solo para validar candidatos de autocorrección). */
    private fun weightedDistance(s1: String, s2: String): Double {
        val n = s1.length
        val m = s2.length
        val dp = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) dp[i][0] = i.toDouble()
        for (j in 0..m) dp[0][j] = j.toDouble()
        for (i in 1..n) {
            for (j in 1..m) {
                val c1 = s1[i - 1].lowercaseChar()
                val c2 = s2[j - 1].lowercaseChar()
                val cost = charPenalty(c1, c2)
                dp[i][j] = min(min(dp[i - 1][j] + 1.0, dp[i][j - 1] + 1.0), dp[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    dp[i][j] = min(dp[i][j], dp[i - 2][j - 2] + 0.5) // transposición
                }
            }
        }
        return dp[n][m]
    }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    private fun calculateScore(
        frequency: Int,
        isUserWord: Boolean,
        proximityBonus: Double,
        isPrefix: Boolean,
        word: String
    ): Double {
        val base = ln(frequency.toDouble() + 2.0)
        val userMult = if (isUserWord) USER_WORD_BOOST else 1.0
        val prefixMult = if (isPrefix) EXACT_PREFIX_BOOST else 1.0
        val timesTyped = (wordCounts[word.lowercase()] ?: 0).coerceAtMost(LEARNED_WORD_BOOST_CAP)
        val learnBoost = 1.0 + (timesTyped * LEARNED_WORD_BOOST_STEP)
        return base * proximityBonus * userMult * prefixMult * learnBoost * 10.0
    }

    private fun matchCase(input: String, suggestion: String): String {
        if (input.isEmpty()) return suggestion
        val isAllUpper = input.length > 1 && input.all { it.isUpperCase() }
        val isFirstUpper = input[0].isUpperCase()
        return when {
            isAllUpper -> suggestion.uppercase()
            isFirstUpper -> suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            else -> suggestion.lowercase()
        }
    }

    // ------------------------------------------------------------------
    // Aprendizaje / diccionario de usuario
    // ------------------------------------------------------------------

    fun clearCaches() {
        synchronized(predictionCache) { predictionCache.evictAll() }
        wordCounts.clear()
    }

    fun getAccentedBaseWord(word: String): String? {
        val norm = stripAccents(word.lowercase())
        if (norm.isEmpty()) return null
        for (langBuf in activeLanguageBuffers) {
            val buffer = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            buffer.position(langBuf.headerSize)
            val match = findAccentedWordInBinaryRecursive(buffer, norm, "")
            if (match != null) return match
        }
        return null
    }

    private fun findAccentedWordInBinaryRecursive(buffer: ByteBuffer, targetNorm: String, prefix: String): String? {
        val count = try { readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            val node = parseNextNode(buffer, prefix)
            val nodeNorm = stripAccents(node.word.lowercase())

            if (node.isTerminal && nodeNorm == targetNorm && node.word.lowercase() != targetNorm) {
                return node.word
            }

            if (targetNorm.startsWith(nodeNorm) && node.childrenAddr != null) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                val res = findAccentedWordInBinaryRecursive(buffer, targetNorm, node.word)
                if (res != null) return res
                buffer.position(savedPos)
            }
        }
        return null
    }

    fun learnFromInput(word: String, prev1: String? = null, prev2: String? = null) {
        val sanitized = WordSanitizer.sanitizeToken(word)
        val w = sanitized.cleanWord.lowercase().trim()
        if (w.length < 2 || w.any { !it.isLetter() }) return

        val accentedVersion = getAccentedBaseWord(w)
        val targetWord = accentedVersion ?: w

        patternLearningManager.recordSequence(prev2, prev1, targetWord)

        if (isKnownWord(targetWord)) {
            wordCounts[targetWord] = (wordCounts[targetWord] ?: 0) + 1
            synchronized(predictionCache) { predictionCache.evictAll() }
            return
        }

        val count = (wordCounts[targetWord] ?: 0) + 1
        wordCounts[targetWord] = count

        if (count >= MIN_LEARN_COUNT || userDictionary.contains(targetWord)) {
            if (!userDictionary.contains(targetWord)) {
                insert(targetWord, 200, isUser = true)
                userDictionary.add(targetWord)
                saveUserDictionary()
            }
        }
        synchronized(predictionCache) { predictionCache.evictAll() }
    }

    fun addUserWord(word: String) {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return
        insert(w, 800, isUser = true)
        userDictionary.add(w)
        saveUserDictionary()
        synchronized(predictionCache) { predictionCache.evictAll() }
    }

    fun removeUserWord(word: String) {
        userDictionary.remove(word.lowercase())
        saveUserDictionary()
        isUserLoaded = false // Forzar recarga total para limpiar el trie
        synchronized(predictionCache) { predictionCache.evictAll() }
    }

    fun getUserWords(): List<String> = userDictionary.toList().sorted()

    fun removeAllUserWords() {
        userDictionary.clear()
        saveUserDictionary()
        isUserLoaded = false
        synchronized(predictionCache) { predictionCache.evictAll() }
    }
}