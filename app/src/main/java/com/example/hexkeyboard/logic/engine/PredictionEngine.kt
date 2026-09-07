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
    }

    private val trie = TrieNode() // Diccionario de usuario + palabras aprendidas
    private var baseDictBuffer: ByteBuffer? = null
    private var baseDictHeaderSize: Int = 0
    private val bigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private val userDictionary = mutableSetOf<String>()
    private val wordCounts = mutableMapOf<String, Int>() // Aprendizaje gradual estilo Gboard
    private val predictionCache = LruCache<String, List<Suggestion>>(CACHE_SIZE)

    private var isBaseLoaded = false
    private var isUserLoaded = false
    private var currentLanguage = "es"

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
        predictionCache.evictAll()
    }

    private val accentMap = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
        'ü' to 'u', 'ñ' to 'n'
    )

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

    suspend fun initialize(lang: String = "es", forceUserDictReload: Boolean = false, includeBase: Boolean = true) {
        if (!forceUserDictReload && isUserLoaded && isBaseLoaded && currentLanguage == lang) return
        currentLanguage = lang
        predictionCache.evictAll()

        withContext(Dispatchers.IO) {
            if (forceUserDictReload || !isUserLoaded) {
                userDictionary.clear()
                loadUserDictionary()
                isUserLoaded = true
            }
            if (includeBase && (!isBaseLoaded || currentLanguage != lang)) {
                loadBaseDictionary(lang)
                isBaseLoaded = true
            }
            loadBigrams()
        }
    }

    private fun loadBaseDictionary(lang: String) {
        try {
            val fileName = "main_$lang.dict"
            val bytes = context.assets.open(fileName).use { it.readBytes() }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.int
            if (magic != 0x9BC13AFE.toInt()) {
                Log.e("PredictionEngine", "Magic inválido: ${Integer.toHexString(magic)}")
                return
            }

            buffer.short // version
            buffer.short // flags
            baseDictHeaderSize = buffer.int
            baseDictBuffer = buffer

            Log.d("PredictionEngine", "Diccionario base '$lang' cargado en buffer binario")
        } catch (e: Exception) {
            Log.e("PredictionEngine", "Error cargando diccionario base: ${e.message}")
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
        if (bigrams.isEmpty()) {
            addBigram("muchas", "gracias")
            addBigram("buenos", "días")
            addBigram("buenas", "noches")
            addBigram("hola", "cómo")
            addBigram("cómo", "estás")
            addBigram("estoy", "bien")
            addBigram("por", "favor")
            addBigram("de", "nada")
            addBigram("nos", "vemos")
            addBigram("hasta", "luego")
        }
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

    private fun addBigram(w1: String, w2: String) {
        val lowW1 = w1.lowercase()
        val lowW2 = w2.lowercase()
        val nextWords = bigrams.getOrPut(lowW1) { mutableMapOf() }
        nextWords[lowW2] = (nextWords[lowW2] ?: 0) + 1

        if (bigrams.size > BIGRAM_LIMIT) {
            bigrams.remove(bigrams.keys.first())
        }
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
        val buffer = baseDictBuffer?.duplicate()?.order(ByteOrder.BIG_ENDIAN) ?: return false
        buffer.position(baseDictHeaderSize)
        return isKnownWordInBinaryRecursive(buffer, target, "")
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

    fun getSuggestions(currentWord: String, previousWord: String? = null, limit: Int = 5): List<Suggestion> {
        if (!isBaseLoaded) return emptyList()

        val lowPrefix = currentWord.lowercase()
        val cacheKey = "curr:${lowPrefix}_prev:${previousWord}_lang:$currentLanguage"
        predictionCache.get(cacheKey)?.let { cached ->
            return cached.map { it.copy(text = matchCase(currentWord, it.text)) }
        }

        val result = mutableListOf<Suggestion>()

        // 1. Predicción de siguiente palabra basada en contexto (bigramas)
        if (currentWord.isEmpty() && previousWord != null) {
            bigrams[previousWord.lowercase()]?.entries
                ?.sortedByDescending { it.value }
                ?.take(limit)
                ?.forEach {
                    result.add(Suggestion(it.key, it.value.toDouble() * 100.0, isNextWord = true, confidence = 0.8f))
                }
        }

        if (currentWord.isNotEmpty()) {
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

        // 4. Ranking final integrando contexto de bigramas
        val finalResult = result
            .distinctBy { it.text.lowercase() }
            .map { sug ->
                var finalScore = sug.score
                if (previousWord != null) {
                    val bigramFreq = bigrams[previousWord.lowercase()]?.get(sug.text.lowercase()) ?: 0
                    if (bigramFreq > 0) {
                        finalScore *= (1.5 + (bigramFreq * 0.5))
                    }
                }
                sug.copy(score = finalScore)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.copy(text = matchCase(currentWord, it.text)) }

        predictionCache.put(cacheKey, finalResult)
        return finalResult
    }

    /**
     * Algoritmo de coincidencia de gestos (Glide Typing).
     * Evalúa palabras del diccionario contra la trayectoria del dedo.
     */
    fun getGestureSuggestions(
        points: List<PointF>,
        keyMap: List<CharPoint>,
        limit: Int = 5
    ): List<Suggestion> {
        if (points.size < 2 || !isBaseLoaded) return emptyList()

        val results = mutableListOf<Suggestion>()
        val startPoint = points.first()
        val endPoint = points.last()

        // 1. Filtrar teclas por proximidad a los puntos de inicio y fin para reducir el espacio de búsqueda
        val startChars = keyMap.filter { hypot(it.x - startPoint.x, it.y - startPoint.y) < 150f }.map { it.char }
        val endChars = keyMap.filter { hypot(it.x - endPoint.x, it.y - endPoint.y) < 150f }.map { it.char }

        if (startChars.isEmpty() || endChars.isEmpty()) return emptyList()

        // 2. Buscar en memoria
        searchGestureInMemory(trie, "", points, keyMap, results)

        // 3. Buscar en binario
        baseDictBuffer?.let { buffer ->
            val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(baseDictHeaderSize)
            searchGestureInBinary(dup, "", points, keyMap, results)
        }

        return results
            .distinctBy { it.text.lowercase() }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun searchGestureInMemory(
        node: TrieNode,
        word: String,
        points: List<PointF>,
        keyMap: List<CharPoint>,
        results: MutableList<Suggestion>
    ) {
        if (results.size > 100) return

        if (node.isWord && word.length >= 2) {
            val score = calculateGestureScore(word, points, keyMap, node.frequency)
            if (score > 0) {
                results.add(Suggestion(word, score, isGesture = true, confidence = (score / 1000.0).toFloat().coerceIn(0f, 1f)))
            }
        }

        node.children.forEach { (char, child) ->
            // Poda básica: el carácter debe aparecer en algún punto después del anterior
            if (isCharPossibleInGesture(word, char, points, keyMap)) {
                searchGestureInMemory(child, word + char, points, keyMap, results)
            }
        }
    }

    private fun searchGestureInBinary(
        buffer: ByteBuffer,
        prefix: String,
        points: List<PointF>,
        keyMap: List<CharPoint>,
        results: MutableList<Suggestion>
    ) {
        if (results.size > 100) return

        val count = readPtNodeCount(buffer)
        repeat(count) {
            val node = parseNextNode(buffer, prefix)
            val segment = node.word.substring(prefix.length)

            // Verificar si el segmento completo es posible en el gesto
            var possible = true
            var tempWord = prefix
            for (ch in segment) {
                if (!isCharPossibleInGesture(tempWord, ch, points, keyMap)) {
                    possible = false
                    break
                }
                tempWord += ch
            }

            if (possible) {
                if (node.isTerminal && node.word.length >= 2) {
                    val score = calculateGestureScore(node.word, points, keyMap, node.frequency)
                    if (score > 0) {
                        results.add(Suggestion(node.word, score, isGesture = true, confidence = (score / 1000.0).toFloat().coerceIn(0f, 1f)))
                    }
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    searchGestureInBinary(buffer, node.word, points, keyMap, results)
                    buffer.position(savedPos)
                }
            }
        }
    }

    private fun isCharPossibleInGesture(word: String, nextChar: Char, points: List<PointF>, keyMap: List<CharPoint>): Boolean {
        if (word.isEmpty()) {
            // El primer carácter debe estar cerca del inicio
            val cp = keyMap.find { it.char == nextChar } ?: return false
            return hypot(cp.x - points.first().x, cp.y - points.first().y) < 180f
        }

        // El siguiente carácter debe aparecer en la trayectoria después del anterior
        // Por simplicidad, buscamos si existe algún punto que esté "cerca" de la tecla
        val cp = keyMap.find { it.char == nextChar } ?: return false

        // En un teclado hexagonal, permitimos un radio de búsqueda generoso
        val threshold = 160f
        return points.any { hypot(it.x - cp.x, it.y - cp.y) < threshold }
    }

    private fun calculateGestureScore(word: String, points: List<PointF>, keyMap: List<CharPoint>, freq: Int): Double {
        var totalDist = 0.0
        var lastPointIdx = 0

        for (char in word) {
            val cp = keyMap.find { it.char == char } ?: return 0.0
            var minDist = Double.MAX_VALUE
            var bestIdx = lastPointIdx

            // Buscamos el punto más cercano a esta tecla, empezando desde donde nos quedamos
            for (i in lastPointIdx until points.size) {
                val d = hypot(cp.x - points[i].x, cp.y - points[i].y).toDouble()
                if (d < minDist) {
                    minDist = d
                    bestIdx = i
                }
            }
            totalDist += minDist
            lastPointIdx = bestIdx
        }

        // El último carácter debe estar cerca del final del gesto
        val lastCharPos = keyMap.find { it.char == word.last() } ?: return 0.0
        val distToEnd = hypot(lastCharPos.x - points.last().x, lastCharPos.y - points.last().y)
        if (distToEnd > 200f) return 0.0

        val avgDist = totalDist / word.length
        if (avgDist > 120.0) return 0.0

        val proximityFactor = 1.0 / (avgDist / 50.0 + 1.0)
        return ln(freq.toDouble() + 2.0) * proximityFactor * 100.0
    }

    /**
     * Autocorrección estilo Gboard: solo propone reemplazo si la palabra
     * escrita NO es válida, existe un candidato razonablemente cercano y ese
     * candidato tiene confianza suficiente. Evita "corregir" palabras raras
     * pero legítimas (nombres propios cortos, jerga ya aprendida, etc.).
     */
    fun getAutocorrection(typedWord: String, previousWord: String? = null): Suggestion? {
        val cleaned = typedWord.trim()
        if (cleaned.length < AUTOCORRECT_MIN_WORD_LEN) return null
        if (isKnownWord(cleaned)) return null

        val candidates = getSuggestions(cleaned, previousWord, limit = 3)
        val best = candidates.filter { it.isCorrection }.maxByOrNull { it.score } ?: return null

        val dist = weightedDistance(cleaned.lowercase(), best.text.lowercase())
        if (dist > AUTOCORRECT_MAX_DISTANCE) return null
        if (best.confidence < AUTOCORRECT_MIN_CONFIDENCE) return null

        return best
    }

    private fun findPrefixMatches(prefix: String, result: MutableList<Suggestion>) {
        // 1. Buscar en el trie de memoria (usuario + aprendidas)
        var current: TrieNode? = trie
        for (char in prefix) {
            current = current?.children?.get(char)
            if (current == null) break
        }
        current?.let { node -> collectAllFromNode(node, prefix, result) }

        // 2. Buscar en el buffer binario (diccionario base)
        baseDictBuffer?.let { buffer ->
            val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(baseDictHeaderSize)
            searchPrefixInBinary(dup, prefix, "", result)
        }
    }

    private fun collectAllFromNode(node: TrieNode, word: String, result: MutableList<Suggestion>) {
        if (node.isWord) {
            val score = calculateScore(node.frequency, node.isUserWord, 1.0, isPrefix = true, word = word)
            result.add(Suggestion(word, score, confidence = 0.9f))
        }
        if (result.size > MAX_INTERNAL_RESULTS) return
        node.children.forEach { (char, child) -> collectAllFromNode(child, word + char, result) }
    }

    private fun searchPrefixInBinary(buffer: ByteBuffer, target: String, prefix: String, result: MutableList<Suggestion>) {
        if (result.size > MAX_INTERNAL_RESULTS) return

        val count = readPtNodeCount(buffer)
        repeat(count) {
            val node = parseNextNode(buffer, prefix)

            if (node.word.startsWith(target) || target.startsWith(node.word)) {
                if (node.isTerminal && node.word.startsWith(target)) {
                    val score = calculateScore(node.frequency, false, 1.0, isPrefix = true, word = node.word)
                    result.add(Suggestion(node.word, score, confidence = 0.9f))
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    searchPrefixInBinary(buffer, target, node.word, result)
                    buffer.position(savedPos)
                }
            }
            // Si no es prefijo compatible, simplemente no se desciende: el
            // cursor del buffer ya quedó posicionado en el siguiente hermano.
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

        baseDictBuffer?.let { buffer ->
            val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(baseDictHeaderSize)
            searchFuzzyInBinary(dup, "", target, 0.0, maxDist, fuzzyResults)
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
        results: MutableList<Suggestion>
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
                    val score = calculateScore(node.frequency, false, proximityBonus, isPrefix = false, word = node.word)
                    results.add(Suggestion(node.word, score, isCorrection = true, confidence = proximityBonus.toFloat()))
                }
            }

            if (node.childrenAddr != null && newDist <= maxDist) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                searchFuzzyInBinary(buffer, node.word, target, newDist, maxDist, results)
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
        predictionCache.evictAll()
        wordCounts.clear()
    }

    fun learnFromInput(word: String, prev: String?) {
        val w = word.lowercase().trim()
        if (w.length < 2 || w.any { !it.isLetter() }) return

        // 1. Si ya es una palabra conocida, solo reforzamos el bigrama
        if (isKnownWord(w)) {
            if (prev != null) addBigram(prev, w)
            wordCounts[w] = (wordCounts[w] ?: 0) + 1
            predictionCache.evictAll()
            return
        }

        // 2. Conteo estilo Gboard para evitar aprender typos accidentales
        val count = (wordCounts[w] ?: 0) + 1
        wordCounts[w] = count

        if (count >= MIN_LEARN_COUNT || userDictionary.contains(w)) {
            if (!userDictionary.contains(w)) {
                insert(w, 200, isUser = true)
                userDictionary.add(w)
                saveUserDictionary()
            }
            if (prev != null) addBigram(prev, w)
        }
        predictionCache.evictAll()
    }

    fun addUserWord(word: String) {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return
        insert(w, 800, isUser = true)
        userDictionary.add(w)
        saveUserDictionary()
        predictionCache.evictAll()
    }

    fun removeUserWord(word: String) {
        userDictionary.remove(word.lowercase())
        saveUserDictionary()
        isUserLoaded = false // Forzar recarga total para limpiar el trie
        predictionCache.evictAll()
    }

    fun getUserWords(): List<String> = userDictionary.toList().sorted()

    fun removeAllUserWords() {
        userDictionary.clear()
        saveUserDictionary()
        isUserLoaded = false
        predictionCache.evictAll()
    }
}