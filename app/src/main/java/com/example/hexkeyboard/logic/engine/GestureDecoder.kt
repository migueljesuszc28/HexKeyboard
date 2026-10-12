package com.example.hexkeyboard.logic.engine

import android.graphics.PointF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

/**
 * Decodificador de gestos de deslizamiento (Glide Typing) optimizado para el teclado hexagonal.
 * Precalcula mapas de búsqueda O(1), distancias cuadráticas y normalización de acentos.
 */
class GestureDecoder(
    private val stripAccentsFn: (String) -> String
) {

    fun getGestureSuggestions(
        points: List<PointF>,
        keyMap: List<PredictionEngine.CharPoint>,
        activeBuffers: List<BinaryDictionary.LanguageBuffer>,
        userDict: UserDictionary,
        binaryDict: BinaryDictionary,
        limit: Int = 5
    ): List<PredictionEngine.Suggestion> {
        if (points.size < 2 || keyMap.isEmpty()) return emptyList()

        val charPointMap = keyMap.associateBy { stripAccentsChar(it.char) }
        val hexRadius = computeHexRadius(keyMap)
        val resampledPoints = resampleGesturePoints(points, 35)
        if (resampledPoints.size < 2) return emptyList()

        val cornerPoints = findCornerPoints(resampledPoints)
        val startPoint = resampledPoints.first()
        val endPoint = resampledPoints.last()

        val maxEdgeDistSq = (hexRadius * 2.5f) * (hexRadius * 2.5f)
        val startChars = keyMap.filter { distSq(it.x, it.y, startPoint.x, startPoint.y) <= maxEdgeDistSq }
            .map { stripAccentsChar(it.char) }.toSet()
        val endChars = keyMap.filter { distSq(it.x, it.y, endPoint.x, endPoint.y) <= maxEdgeDistSq }
            .map { stripAccentsChar(it.char) }.toSet()

        if (startChars.isEmpty() || endChars.isEmpty()) return emptyList()

        val results = mutableListOf<PredictionEngine.Suggestion>()

        // 1. Buscar en diccionario de usuario
        userDict.readLock { root ->
            searchGestureInMemory(root, "", resampledPoints, cornerPoints, charPointMap, results, hexRadius, 0, startChars, endChars, userDict)
        }

        // 2. Buscar en diccionarios binarios
        for (langBuf in activeBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.85
            searchGestureInBinary(dup, "", resampledPoints, cornerPoints, charPointMap, results, hexRadius, 0, startChars, endChars, binaryDict, userDict, langWeight)
        }

        return results
            .groupBy { it.text.lowercase() }
            .mapValues { entry -> entry.value.maxByOrNull { it.score }!! }
            .values
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun stripAccentsChar(c: Char): Char {
        return when (c.lowercaseChar()) {
            'á' -> 'a'
            'é' -> 'e'
            'í' -> 'i'
            'ó' -> 'o'
            'ú' -> 'u'
            'ü' -> 'u'
            else -> c.lowercaseChar()
        }
    }

    private fun distSq(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    private fun computeHexRadius(keyMap: List<PredictionEngine.CharPoint>): Float {
        if (keyMap.size < 2) return 100f
        var minDistSum = 0f
        var count = 0
        for (i in keyMap.indices) {
            var minDSq = Float.MAX_VALUE
            for (j in keyMap.indices) {
                if (i == j) continue
                val dSq = distSq(keyMap[i].x, keyMap[i].y, keyMap[j].x, keyMap[j].y)
                if (dSq > 1f && dSq < minDSq) {
                    minDSq = dSq
                }
            }
            if (minDSq < Float.MAX_VALUE) {
                minDistSum += kotlin.math.sqrt(minDSq)
                count++
            }
        }
        return if (count > 0) (minDistSum / count) / 2f else 100f
    }

    private fun resampleGesturePoints(points: List<PointF>, targetCount: Int): List<PointF> {
        val cleanPoints = mutableListOf<PointF>()
        for (p in points) {
            if (cleanPoints.isEmpty() || distSq(p.x, p.y, cleanPoints.last().x, cleanPoints.last().y) > 9f) {
                cleanPoints.add(p)
            }
        }
        if (cleanPoints.size <= 2) return cleanPoints

        var totalLength = 0f
        for (i in 0 until cleanPoints.size - 1) {
            totalLength += kotlin.math.sqrt(distSq(cleanPoints[i + 1].x, cleanPoints[i + 1].y, cleanPoints[i].x, cleanPoints[i].y))
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
            val segLen = kotlin.math.sqrt(distSq(nextPt.x, nextPt.y, currPt.x, currPt.y))

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

            val len1Sq = v1x * v1x + v1y * v1y
            val len2Sq = v2x * v2x + v2y * v2y

            if (len1Sq > 0f && len2Sq > 0f) {
                val len1 = kotlin.math.sqrt(len1Sq)
                val len2 = kotlin.math.sqrt(len2Sq)
                val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
                if (dot < 0.75f) {
                    corners.add(pCurr)
                }
            }
        }
        return corners
    }

    private fun searchGestureInMemory(
        node: UserDictionary.TrieNode,
        word: String,
        resampledPoints: List<PointF>,
        cornerPoints: List<PointF>,
        charPointMap: Map<Char, PredictionEngine.CharPoint>,
        results: MutableList<PredictionEngine.Suggestion>,
        hexRadius: Float,
        lastPointIndex: Int,
        startChars: Set<Char>,
        endChars: Set<Char>,
        userDict: UserDictionary
    ) {
        if (results.size > 120) return

        if (node.isWord && word.length >= 2) {
            val boost = if (node.isUserWord) 2.6 else 1.0
            val score = calculateGestureScore(word, resampledPoints, cornerPoints, charPointMap, node.frequency, hexRadius, endChars) * boost
            if (score > 0) {
                val confidence = (score / 350.0).toFloat().coerceIn(0f, 1f)
                results.add(PredictionEngine.Suggestion(word, score, isGesture = true, confidence = confidence))
            }
        }

        node.children.forEach { (char, child) ->
            val nextIndex = isCharPossibleInGesture(word, char, resampledPoints, charPointMap, hexRadius, lastPointIndex, startChars)
            if (nextIndex >= 0) {
                searchGestureInMemory(child, word + char, resampledPoints, cornerPoints, charPointMap, results, hexRadius, nextIndex, startChars, endChars, userDict)
            }
        }
    }

    private fun searchGestureInBinary(
        buffer: ByteBuffer,
        prefix: String,
        resampledPoints: List<PointF>,
        cornerPoints: List<PointF>,
        charPointMap: Map<Char, PredictionEngine.CharPoint>,
        results: MutableList<PredictionEngine.Suggestion>,
        hexRadius: Float,
        lastPointIndex: Int,
        startChars: Set<Char>,
        endChars: Set<Char>,
        binaryDict: BinaryDictionary,
        userDict: UserDictionary,
        langWeight: Double = 1.0
    ) {
        if (results.size > 120) return

        val count = try { binaryDict.readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return@repeat
            val node = binaryDict.parseNextNode(buffer, prefix)
            val segment = node.word.substring(prefix.length)

            var possible = true
            var tempWord = prefix
            var currIndex = lastPointIndex

            for (ch in segment) {
                val nextIdx = isCharPossibleInGesture(tempWord, ch, resampledPoints, charPointMap, hexRadius, currIndex, startChars)
                if (nextIdx < 0) {
                    possible = false
                    break
                }
                tempWord += ch
                currIndex = nextIdx
            }

            if (possible) {
                if (node.isTerminal && node.word.length >= 2) {
                    val userBoost = if (userDict.contains(node.word)) 2.6 else 1.0
                    val score = calculateGestureScore(node.word, resampledPoints, cornerPoints, charPointMap, node.frequency, hexRadius, endChars) * langWeight * userBoost
                    if (score > 0) {
                        val confidence = ((score / 350.0) * langWeight).toFloat().coerceIn(0f, 1f)
                        results.add(PredictionEngine.Suggestion(node.word, score, isGesture = true, confidence = confidence))
                    }
                }
                if (node.childrenAddr != null) {
                    val savedPos = buffer.position()
                    buffer.position(node.addressBase + node.childrenAddr)
                    searchGestureInBinary(buffer, node.word, resampledPoints, cornerPoints, charPointMap, results, hexRadius, currIndex, startChars, endChars, binaryDict, userDict, langWeight)
                    buffer.position(savedPos)
                }
            }
        }
    }

    private fun isCharPossibleInGesture(
        word: String,
        nextChar: Char,
        resampledPoints: List<PointF>,
        charPointMap: Map<Char, PredictionEngine.CharPoint>,
        hexRadius: Float,
        lastPointIndex: Int,
        startChars: Set<Char>
    ): Int {
        val normChar = stripAccentsChar(nextChar)
        if (word.isEmpty()) {
            if (normChar !in startChars) return -1
            return 0
        }
        val cp = charPointMap[normChar] ?: return -1
        val thresholdSq = (hexRadius * 2.2f) * (hexRadius * 2.2f)
        for (i in lastPointIndex until resampledPoints.size) {
            if (distSq(cp.x, cp.y, resampledPoints[i].x, resampledPoints[i].y) < thresholdSq) {
                return i
            }
        }
        return -1
    }

    private fun calculateGestureScore(
        word: String,
        resampledPoints: List<PointF>,
        cornerPoints: List<PointF>,
        charPointMap: Map<Char, PredictionEngine.CharPoint>,
        freq: Int,
        hexRadius: Float,
        endChars: Set<Char>
    ): Double {
        if (word.length < 2) return 0.0
        val lastNormChar = stripAccentsChar(word.last())
        if (lastNormChar !in endChars) return 0.0

        var totalNormalizedDist = 0.0
        var lastPointIdx = 0
        var cornerMatchBonus = 0.0

        for (char in word) {
            val norm = stripAccentsChar(char)
            val cp = charPointMap[norm] ?: return 0.0
            var minDistSq = Double.MAX_VALUE
            var bestIdx = lastPointIdx

            for (i in lastPointIdx until resampledPoints.size) {
                val dSq = distSq(cp.x, cp.y, resampledPoints[i].x, resampledPoints[i].y).toDouble()
                if (dSq < minDistSq) {
                    minDistSq = dSq
                    bestIdx = i
                }
            }
            val minDist = kotlin.math.sqrt(minDistSq)
            if (minDist > hexRadius * 2.5) return 0.0
            totalNormalizedDist += minDist / hexRadius
            lastPointIdx = bestIdx

            val cornerThresholdSq = (hexRadius * 1.2f) * (hexRadius * 1.2f)
            if (cornerPoints.any { distSq(cp.x, cp.y, it.x, it.y) < cornerThresholdSq }) {
                cornerMatchBonus += 0.25
            }
        }

        val lastCharPos = charPointMap[lastNormChar] ?: return 0.0
        val distToEnd = kotlin.math.sqrt(distSq(lastCharPos.x, lastCharPos.y, resampledPoints.last().x, resampledPoints.last().y))
        if (distToEnd > hexRadius * 2.2f) return 0.0

        val avgDist = totalNormalizedDist / word.length
        if (avgDist > 2.2) return 0.0

        val proximityFactor = 1.0 / (1.0 + 0.6 * avgDist)
        val freqScore = ln(freq.toDouble() + 2.0)
        return freqScore * proximityFactor * (1.0 + cornerMatchBonus) * 100.0
    }
}
