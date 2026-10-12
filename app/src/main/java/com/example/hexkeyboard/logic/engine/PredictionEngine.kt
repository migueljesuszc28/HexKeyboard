package com.example.hexkeyboard.logic.engine

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/**
 * Motor de predicción, autocorrección y decodificación de gestos de nivel profesional.
 * Orquesta los diccionarios binarios, el diccionario de usuario, el decodificador de gestos,
 * el modelo espacial BeamSearch y el autocorrector basado en confianza.
 */
class PredictionEngine(private val context: Context) {

    companion object {
        private const val CACHE_SIZE = 250
        private const val TOP_K_LIMIT = 60
        private const val MIN_LEARN_COUNT = 2

        // Pesos de scoring
        private const val USER_WORD_BOOST = 2.6
        private const val EXACT_PREFIX_BOOST = 1.0
        private const val EXACT_MATCH_BOOST = 10.0
        private const val ACCENT_MATCH_BOOST = 5.0
        private const val LEARNED_WORD_BOOST_STEP = 0.05
        private const val LEARNED_WORD_BOOST_CAP = 10

        private val ACCENT_MAP_STATIC = mapOf(
            'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u',
            'ü' to 'u', 'ñ' to 'n'
        )

        fun stripAccentsStatic(str: String): String {
            if (str.isEmpty()) return str
            val sb = java.lang.StringBuilder(str.length)
            for (ch in str) {
                val mapped = ACCENT_MAP_STATIC[ch] ?: ch
                sb.append(mapped)
            }
            return sb.toString()
        }

        fun stripAccents(str: String): String {
            if (str.isEmpty()) return str
            val sb = java.lang.StringBuilder(str.length)
            for (ch in str) {
                when (val c = ch.lowercaseChar()) {
                    'á' -> sb.append('a')
                    'é' -> sb.append('e')
                    'í' -> sb.append('i')
                    'ó' -> sb.append('o')
                    'ú' -> sb.append('u')
                    'ü' -> sb.append('u')
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }
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

    data class UserWordEntry(val word: String, val timestamp: Long)

    private val binaryDictionary = BinaryDictionary()
    private val userDictionary = UserDictionary(context)
    private val patternLearningManager = PatternLearningManager(context)
    private val calibrationManager = TouchCalibrationManager()
    private val beamSearchDecoder = BeamSearchDecoder(calibrationManager)
    private val diacriticManager = DiacriticManager()

    private val gestureDecoder = GestureDecoder { stripAccents(it) }
    private val autocorrector = Autocorrector(
        diacriticManager = diacriticManager,
        patternLearningManager = patternLearningManager,
        stripAccentsFn = { stripAccents(it) },
        isKnownWordFn = { isKnownWord(it) },
        weightedDistanceFn = { s1, s2 -> weightedDistance(s1, s2) }
    )

    @Volatile private var keyCenters: Map<Char, PointF> = emptyMap()
    private val predictionCache = LruCache<String, List<Suggestion>>(CACHE_SIZE)

    @Volatile private var isBaseLoaded = false
    @Volatile private var isUserLoaded = false
    @Volatile private var currentLanguage = "es"

    fun updateKeyCenters(centers: Map<Char, PointF>) {
        this.keyCenters = centers
    }

    fun setLayoutType(type: String) {
        predictionCache.evictAll()
    }

    fun registerConfirmedWordTouch(touchPoints: List<TouchPoint>, confirmedWord: String) {
        val minLen = minOf(touchPoints.size, confirmedWord.length)
        val centers = keyCenters
        for (i in 0 until minLen) {
            val char = confirmedWord[i].lowercaseChar()
            val center = centers[char]
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

        val currentLoadedLangs = binaryDictionary.activeBuffers.map { it.langCode }
        val primaryChanged = currentLanguage != lang
        val languagesChanged = currentLoadedLangs != targetLangs

        if (!forceUserDictReload && isUserLoaded && isBaseLoaded && !primaryChanged && !languagesChanged) return

        currentLanguage = lang
        predictionCache.evictAll()

        withContext(Dispatchers.IO) {
            if (forceUserDictReload || !isUserLoaded) {
                userDictionary.loadUserDictionary()
                isUserLoaded = true
            }
            if (includeBase && (!isBaseLoaded || primaryChanged || languagesChanged)) {
                binaryDictionary.loadDictionaries(context, lang, secondaryLangs)
                isBaseLoaded = binaryDictionary.activeBuffers.isNotEmpty()
            }
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    fun isKnownWord(word: String): Boolean {
        val lower = word.lowercase()
        val norm = stripAccents(lower)

        if (userDictionary.isWordInTrie(lower) || (norm != lower && userDictionary.isWordInTrie(norm))) {
            return true
        }
        return isKnownWordInBinary(lower) || (norm != lower && isKnownWordInBinary(norm))
    }

    private fun isKnownWordInBinary(target: String): Boolean {
        val targetLower = target.lowercase()
        val targetNorm = stripAccents(targetLower)
        for (langBuf in binaryDictionary.activeBuffers) {
            val buffer = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            buffer.position(langBuf.headerSize)
            if (isKnownWordInBinaryRecursive(buffer, targetLower, targetNorm, "")) return true
        }
        return false
    }

    private fun isKnownWordInBinaryRecursive(
        buffer: ByteBuffer,
        targetLower: String,
        targetNorm: String,
        prefix: String
    ): Boolean {
        val count = try { binaryDictionary.readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return false
            val node = binaryDictionary.parseNextNode(buffer, prefix)
            val nodeLower = node.word.lowercase()
            val nodeNorm = stripAccents(nodeLower)

            if (node.isTerminal && (nodeLower == targetLower || nodeNorm == targetNorm)) {
                return true
            }

            if ((targetLower.startsWith(nodeLower) || targetNorm.startsWith(nodeNorm)) && node.childrenAddr != null) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                if (isKnownWordInBinaryRecursive(buffer, targetLower, targetNorm, node.word)) return true
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

        val activeLangsKey = binaryDictionary.activeBuffers.joinToString(",") { it.langCode }
        val tpHash = if (touchPoints.isNotEmpty()) {
            touchPoints.joinToString(";") { "${(it.x / 10).toInt()},${(it.y / 10).toInt()}" }
        } else ""

        val cacheKey = "curr:${lowPrefix}_p1:${previousWord}_p2:${prev2}_tp:${tpHash}_langs:$activeLangsKey"
        predictionCache.get(cacheKey)?.let { cached ->
            return cached.map { it.copy(text = matchCase(sanitized.cleanWord, it.text)) }
        }

        val result = mutableListOf<Suggestion>()

        // 1. Predicción N-Gramas de siguiente palabra
        if (lowPrefix.isEmpty() && previousWord != null) {
            val localPredictions = patternLearningManager.predictNextWords(prev2, previousWord, limit = limit)
            val binaryPredictions = binaryDictionary.getBinaryBigramPredictions(previousWord, limit = limit) { stripAccents(it) }

            val mergedMap = mutableMapOf<String, Double>()
            localPredictions.forEach { (nextWord, score) -> mergedMap[nextWord.lowercase()] = score }
            binaryPredictions.forEach { (nextWord, score) ->
                val key = nextWord.lowercase()
                mergedMap[key] = maxOf(mergedMap[key] ?: 0.0, score)
            }

            mergedMap.entries.sortedByDescending { it.value }.take(limit).forEach { (nextWord, score) ->
                result.add(Suggestion(nextWord, score, isCorrection = false, isNextWord = true, confidence = 0.85f))
            }
        }

        if (lowPrefix.isNotEmpty()) {
            // 2. Coincidencias de prefijo
            findPrefixMatches(lowPrefix, result)

            // 3. Corrección fuzzy
            val fuzzyMaxDist = when {
                lowPrefix.length <= 2 -> 0.4
                lowPrefix.length <= 4 -> 0.9
                else -> 1.6
            }
            result.addAll(findFuzzyMatches(lowPrefix, fuzzyMaxDist))
        }

        // 4. Re-evaluación probabilística con Beam Search y Modelo Espacial
        val centers = keyCenters
        if (touchPoints.isNotEmpty() && centers.isNotEmpty() && result.isNotEmpty()) {
            val ranked = beamSearchDecoder.rankHypotheses(
                touchPoints = touchPoints,
                suggestions = result,
                keyCenters = centers,
                patternLearningManager = patternLearningManager,
                previousWord = previousWord,
                prev2 = prev2,
                beamWidth = limit
            )
            if (ranked.isNotEmpty()) {
                val origMap = result.groupBy { it.text.lowercase() }
                result.clear()
                result.addAll(ranked.map { candidate ->
                    val orig = origMap[candidate.word.lowercase()]?.firstOrNull()
                    val isCorr = orig?.isCorrection ?: false
                    val conf = orig?.confidence ?: 0.85f
                    Suggestion(candidate.word, candidate.totalScore, isCorrection = isCorr, confidence = conf)
                })
            }
        }

        // 5. Ranking final y deduplicación basada en la mayor puntuación
        val finalResult = result
            .groupBy { it.text.lowercase() }
            .mapValues { entry -> entry.value.maxByOrNull { it.score }!! }
            .values
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.copy(text = matchCase(sanitized.cleanWord, it.text)) }

        predictionCache.put(cacheKey, finalResult)
        return finalResult
    }

    private fun findPrefixMatches(prefix: String, result: MutableList<Suggestion>) {
        userDictionary.readLock { root ->
            var current: UserDictionary.TrieNode? = root
            for (char in prefix) {
                current = current?.children?.get(char)
                if (current == null) break
            }
            if (current != null) {
                collectAllFromNode(current!!, prefix, prefix, result)
            }
        }

        for (langBuf in binaryDictionary.activeBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.95
            searchPrefixInBinary(dup, prefix, "", result, langWeight)
        }
    }

    private fun collectAllFromNode(
        node: UserDictionary.TrieNode,
        word: String,
        target: String,
        result: MutableList<Suggestion>
    ) {
        if (result.size >= TOP_K_LIMIT) return

        if (node.isWord) {
            val isExact = word.lowercase() == target.lowercase()
            val learnCount = userDictionary.getWordCount(word.lowercase())

            val userBoost = if (node.isUserWord) USER_WORD_BOOST else 1.0
            val exactBoost = if (isExact) EXACT_MATCH_BOOST else EXACT_PREFIX_BOOST
            val learnedBoost = 1.0 + min(learnCount * LEARNED_WORD_BOOST_STEP, LEARNED_WORD_BOOST_CAP.toDouble())

            val score = node.frequency * userBoost * exactBoost * learnedBoost

            result.add(Suggestion(
                text = word,
                score = score,
                isCorrection = false,
                confidence = if (isExact) 1.0f else 0.85f
            ))
        }

        node.children.forEach { (char, child) ->
            collectAllFromNode(child, word + char, target, result)
        }
    }

    private fun searchPrefixInBinary(
        buffer: ByteBuffer,
        target: String,
        prefix: String,
        result: MutableList<Suggestion>,
        langWeight: Double
    ) {
        if (result.size >= TOP_K_LIMIT) return

        val count = try { binaryDictionary.readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return@repeat
            val node = binaryDictionary.parseNextNode(buffer, prefix)
            val nodeLower = node.word.lowercase()
            val targetLower = target.lowercase()

            if (nodeLower.startsWith(targetLower)) {
                if (node.isTerminal) {
                    val isExactMatch = nodeLower == targetLower
                    val isAccentMatch = !isExactMatch && stripAccents(nodeLower) == stripAccents(targetLower)

                    val exactBoost = when {
                        isExactMatch -> EXACT_MATCH_BOOST
                        isAccentMatch -> ACCENT_MATCH_BOOST
                        else -> EXACT_PREFIX_BOOST
                    }

                    val learnCount = userDictionary.getWordCount(nodeLower)
                    val userBoost = if (userDictionary.contains(nodeLower)) USER_WORD_BOOST else 1.0
                    val learnedBoost = 1.0 + min(learnCount * LEARNED_WORD_BOOST_STEP, LEARNED_WORD_BOOST_CAP.toDouble())

                    val score = node.frequency * exactBoost * langWeight * userBoost * learnedBoost
                    val confidence = if (isExactMatch) 1.0f else 0.85f

                    result.add(Suggestion(
                        text = node.word,
                        score = score,
                        isCorrection = false,
                        confidence = confidence
                    ))
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    collectAllFromBinary(buffer, node.word, target, result, langWeight)
                    buffer.position(savedPos)
                }
            } else if (targetLower.startsWith(nodeLower) && node.childrenAddr != null) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                searchPrefixInBinary(buffer, target, node.word, result, langWeight)
                buffer.position(savedPos)
            }
        }
    }

    private fun collectAllFromBinary(
        buffer: ByteBuffer,
        prefix: String,
        target: String,
        result: MutableList<Suggestion>,
        langWeight: Double
    ) {
        if (result.size >= TOP_K_LIMIT) return

        val count = try { binaryDictionary.readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return@repeat
            val node = binaryDictionary.parseNextNode(buffer, prefix)
            val nodeLower = node.word.lowercase()

            if (node.isTerminal) {
                val learnCount = userDictionary.getWordCount(nodeLower)
                val userBoost = if (userDictionary.contains(nodeLower)) USER_WORD_BOOST else 1.0
                val learnedBoost = 1.0 + min(learnCount * LEARNED_WORD_BOOST_STEP, LEARNED_WORD_BOOST_CAP.toDouble())

                val score = node.frequency * EXACT_PREFIX_BOOST * langWeight * userBoost * learnedBoost

                result.add(Suggestion(
                    text = node.word,
                    score = score,
                    isCorrection = false,
                    confidence = 0.80f
                ))
            }

            if (node.childrenAddr != null) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                collectAllFromBinary(buffer, node.word, target, result, langWeight)
                buffer.position(savedPos)
            }
        }
    }

    private fun findFuzzyMatches(typed: String, maxDist: Double): List<Suggestion> {
        val fuzzyResults = mutableListOf<Suggestion>()
        val target = typed.lowercase()

        userDictionary.readLock { root ->
            searchFuzzyInMemory(root, "", target, 0.0, maxDist, fuzzyResults)
        }

        for (langBuf in binaryDictionary.activeBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.85
            searchFuzzyInBinary(dup, "", target, 0.0, maxDist, fuzzyResults, langWeight)
        }

        return fuzzyResults
    }

    private fun searchFuzzyInMemory(
        node: UserDictionary.TrieNode,
        currentWord: String,
        target: String,
        accumDist: Double,
        maxDist: Double,
        results: MutableList<Suggestion>
    ) {
        if (accumDist > maxDist || results.size >= TOP_K_LIMIT) return

        if (node.isWord && currentWord.length >= 2) {
            val lenDiff = abs(currentWord.length - target.length)
            val totalDist = accumDist + lenDiff * 0.75
            if (totalDist <= maxDist) {
                val proximityBonus = 1.0 / (1.0 + totalDist)
                val userBoost = if (node.isUserWord) USER_WORD_BOOST else 1.0
                val score = node.frequency * proximityBonus * userBoost

                results.add(Suggestion(
                    text = currentWord,
                    score = score,
                    isCorrection = true,
                    confidence = proximityBonus.toFloat()
                ))
            }
        }

        node.children.forEach { (char, child) ->
            val pos = currentWord.length
            val penalty = if (pos < target.length) {
                charPenalty(char, target[pos])
            } else {
                0.75
            }
            val nextDist = accumDist + penalty
            if (nextDist <= maxDist) {
                searchFuzzyInMemory(child, currentWord + char, target, nextDist, maxDist, results)
            }
        }
    }

    private fun searchFuzzyInBinary(
        buffer: ByteBuffer,
        prefix: String,
        target: String,
        accumDist: Double,
        maxDist: Double,
        results: MutableList<Suggestion>,
        langWeight: Double
    ) {
        if (accumDist > maxDist || results.size >= TOP_K_LIMIT) return

        val count = try { binaryDictionary.readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return@repeat
            val node = binaryDictionary.parseNextNode(buffer, prefix)
            val segment = node.word.substring(prefix.length)

            var segmentDist = 0.0
            var currPos = prefix.length

            for (ch in segment) {
                val pen = if (currPos < target.length) charPenalty(ch, target[currPos]) else 0.75
                segmentDist += pen
                currPos++
            }

            val newDist = accumDist + segmentDist

            if (newDist <= maxDist) {
                if (node.isTerminal && node.word.length >= 2) {
                    val lenDiff = abs(node.word.length - target.length)
                    val totalDist = newDist + lenDiff * 0.75

                    if (totalDist <= maxDist) {
                        val proximityBonus = 1.0 / (1.0 + totalDist)
                        val score = node.frequency * proximityBonus * langWeight

                        results.add(Suggestion(
                            text = node.word,
                            score = score,
                            isCorrection = true,
                            confidence = (proximityBonus * langWeight).toFloat()
                        ))
                    }
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    searchFuzzyInBinary(buffer, node.word, target, newDist, maxDist, results, langWeight)
                    buffer.position(savedPos)
                }
            }
        }
    }

    private fun charPenalty(c1: Char, c2: Char): Double {
        if (c1 == c2) return 0.0
        val norm1 = stripAccents(c1.toString())
        val norm2 = stripAccents(c2.toString())
        if (norm1 == norm2) return 0.15

        val p1 = keyCenters[c1]
        val p2 = keyCenters[c2]
        if (p1 != null && p2 != null) {
            val dx = (p1.x - p2.x).toDouble()
            val dy = (p1.y - p2.y).toDouble()
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            return (dist / 120.0).coerceIn(0.25, 0.9)
        }
        return 0.85
    }

    private fun weightedDistance(s1: String, s2: String): Double {
        if (s1 == s2) return 0.0
        val n1 = stripAccents(s1)
        val n2 = stripAccents(s2)
        if (n1 == n2) return 0.15

        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { DoubleArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i * 0.75
        for (j in 0..len2) dp[0][j] = j * 0.75

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = charPenalty(s1[i - 1], s2[j - 1])
                dp[i][j] = minOf(
                    dp[i - 1][j] + 0.75,
                    dp[i][j - 1] + 0.75,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[len1][len2]
    }

    // ------------------------------------------------------------------
    // Gestos & Autocorrección
    // ------------------------------------------------------------------

    fun getGestureSuggestions(
        points: List<PointF>,
        keyMap: List<CharPoint>,
        limit: Int = 5
    ): List<Suggestion> {
        if (!isBaseLoaded) return emptyList()
        return gestureDecoder.getGestureSuggestions(
            points = points,
            keyMap = keyMap,
            activeBuffers = binaryDictionary.activeBuffers,
            userDict = userDictionary,
            binaryDict = binaryDictionary,
            limit = limit
        )
    }

    fun getAutocorrection(
        typedWord: String,
        previousWord: String? = null,
        prev2: String? = null,
        touchPoints: List<TouchPoint> = emptyList()
    ): Suggestion? {
        val candidates = getSuggestions(typedWord, previousWord = previousWord, prev2 = prev2, touchPoints = touchPoints, limit = 5)
        return autocorrector.getAutocorrection(
            typedWord = typedWord,
            candidates = candidates,
            previousWord = previousWord,
            prev2 = prev2
        )
    }

    fun getBinaryBigramPredictions(previousWord: String, limit: Int = 5): List<Pair<String, Double>> {
        return binaryDictionary.getBinaryBigramPredictions(previousWord, limit, stripAccentsFn = { stripAccents(it) })
    }

    private fun matchCase(input: String, suggestion: String): String {
        if (input.isEmpty() || suggestion.isEmpty()) return suggestion
        val isAllUpper = input.length > 1 && input.all { it.isUpperCase() }
        val isFirstUpper = input[0].isUpperCase()
        return when {
            isAllUpper -> suggestion.uppercase(Locale.ROOT)
            isFirstUpper -> suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            else -> suggestion.lowercase(Locale.ROOT)
        }
    }

    // ------------------------------------------------------------------
    // Aprendizaje
    // ------------------------------------------------------------------

    fun clearCaches() {
        predictionCache.evictAll()
    }

    fun getAccentedBaseWord(word: String): String? {
        val norm = stripAccents(word.lowercase())
        if (norm.isEmpty()) return null
        for (langBuf in binaryDictionary.activeBuffers) {
            val buffer = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            buffer.position(langBuf.headerSize)
            val match = findAccentedWordInBinaryRecursive(buffer, norm, "")
            if (match != null) return match
        }
        return null
    }

    private fun findAccentedWordInBinaryRecursive(buffer: ByteBuffer, targetNorm: String, prefix: String): String? {
        val count = try { binaryDictionary.readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return null
            val node = binaryDictionary.parseNextNode(buffer, prefix)
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
        if (w.length < 2) return

        val targetWord = if (!w.contains('@') && !w.any { it.isDigit() }) {
            getAccentedBaseWord(w) ?: w
        } else {
            w
        }

        patternLearningManager.recordSequence(prev2, prev1, targetWord)

        val count = userDictionary.recordWordCount(targetWord)

        if (isKnownWord(targetWord)) {
            predictionCache.evictAll()
            return
        }

        if (count >= MIN_LEARN_COUNT || userDictionary.contains(targetWord)) {
            if (!userDictionary.contains(targetWord)) {
                userDictionary.addUserWord(targetWord, frequency = 200)
            }
        }
        predictionCache.evictAll()
    }

    fun addUserWord(word: String) {
        userDictionary.addUserWord(word, frequency = 800)
        predictionCache.evictAll()
    }

    fun removeUserWord(word: String) {
        userDictionary.removeUserWord(word)
        predictionCache.evictAll()
    }

    fun getUserWordEntries(): List<UserWordEntry> = userDictionary.getUserWordEntries().map { UserWordEntry(it.word, it.timestamp) }

    fun getUserWords(): List<String> = userDictionary.getUserWords()

    fun removeAllUserWords() {
        userDictionary.removeAllUserWords()
        predictionCache.evictAll()
    }
}
