package com.example.hexkeyboard.logic.engine

import android.graphics.PointF
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Decodificador Beam Search probabilístico inspirado en la arquitectura de Gboard.
 * Evalúa múltiples hipótesis en paralelo combinando la probabilidad del modelo
 * espacial táctil 2D P(Touch|Key) con la probabilidad del modelo de lenguaje P(LM).
 */
class BeamSearchDecoder(
    private val calibrationManager: TouchCalibrationManager = TouchCalibrationManager()
) {

    companion object {
        private const val DEFAULT_BEAM_WIDTH = 15
        private const val DEFAULT_KEY_SIGMA = 40.0f // Desviación estándar espacial base en px
        private const val SPATIAL_WEIGHT = 0.65
        private const val LM_WEIGHT = 0.35
    }

    data class CandidateHypothesis(
        val word: String,
        val spatialScore: Double,
        val lmScore: Double,
        val totalScore: Double
    )

    /**
     * Calcula la probabilidad espacial Gaussiana 2D para un toque (x, y) respecto a la tecla dada.
     */
    fun calculateSpatialProbability(
        touchX: Float,
        touchY: Float,
        keyCenterX: Float,
        keyCenterY: Float,
        sigma: Float = DEFAULT_KEY_SIGMA
    ): Double {
        val calib = calibrationManager.calibrate(touchX, touchY)
        val dx = calib.x - keyCenterX
        val dy = calib.y - keyCenterY
        val distSq = (dx * dx + dy * dy).toDouble()
        val twoSigmaSq = (2.0 * sigma * sigma)
        return exp(-distSq / twoSigmaSq)
    }

    /**
     * Evalúa una secuencia de toques táctiles contra las teclas esperadas de una palabra candidata.
     */
    fun scoreWordSpatial(
        touchPoints: List<TouchPoint>,
        candidateWord: String,
        keyCenters: Map<Char, PointF>
    ): Double {
        if (touchPoints.isEmpty() || candidateWord.isEmpty()) return 1.0

        var totalSpatialProb = 1.0
        val minLen = minOf(touchPoints.size, candidateWord.length)

        for (i in 0 until minLen) {
            val tp = touchPoints[i]
            val candidateChar = candidateWord[i].lowercaseChar()
            val center = keyCenters[candidateChar]

            val spatialProb = if (center != null) {
                calculateSpatialProbability(tp.x, tp.y, center.x, center.y)
            } else {
                0.25 // Probabilidad por defecto para caracteres sin centro conocido
            }

            totalSpatialProb *= spatialProb.coerceAtLeast(0.01)
        }

        // Penalizar diferencia de longitud entre los toques y la palabra candidata
        val lenDiff = kotlin.math.abs(touchPoints.size - candidateWord.length)
        val lenPenalty = exp(-0.4 * lenDiff)

        return totalSpatialProb * lenPenalty
    }

    /**
     * Ordena y combina puntuaciones espaciales y de lenguaje en hipótesis consolidadas.
     */
    fun rankHypotheses(
        touchPoints: List<TouchPoint>,
        suggestions: List<PredictionEngine.Suggestion>,
        keyCenters: Map<Char, PointF>,
        patternLearningManager: PatternLearningManager?,
        previousWord: String?,
        prev2: String?,
        beamWidth: Int = DEFAULT_BEAM_WIDTH
    ): List<CandidateHypothesis> {
        if (suggestions.isEmpty()) return emptyList()

        return suggestions.map { suggestion ->
            val candidateText = suggestion.text
            val spatialProb = if (touchPoints.isNotEmpty() && keyCenters.isNotEmpty()) {
                scoreWordSpatial(touchPoints, candidateText, keyCenters)
            } else {
                1.0
            }

            val ngramScore = patternLearningManager?.calculateContextScore(
                word = candidateText,
                prev1 = previousWord,
                prev2 = prev2
            ) ?: 1.0

            val lmScore = ln(suggestion.score + 2.0) * ngramScore
            val spatialScoreNorm = ln(spatialProb * 100.0 + 1.0)

            val combinedScore = (spatialScoreNorm * SPATIAL_WEIGHT) + (lmScore * LM_WEIGHT)

            CandidateHypothesis(
                word = candidateText,
                spatialScore = spatialScoreNorm,
                lmScore = lmScore,
                totalScore = combinedScore
            )
        }
        .sortedByDescending { it.totalScore }
        .take(beamWidth)
    }
}
