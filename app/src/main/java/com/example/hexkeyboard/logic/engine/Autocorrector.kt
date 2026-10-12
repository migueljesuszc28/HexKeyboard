package com.example.hexkeyboard.logic.engine

/**
 * Encapsula la lógica de autocorrección inteligente basada en confianza,
 * distancia Levenshtein ponderada y margen competitivo respecto al segundo candidato.
 */
class Autocorrector(
    private val diacriticManager: DiacriticManager = DiacriticManager(),
    private val patternLearningManager: PatternLearningManager? = null,
    private val stripAccentsFn: (String) -> String,
    private val isKnownWordFn: (String) -> Boolean,
    private val weightedDistanceFn: (String, String) -> Double
) {

    companion object {
        private const val AUTOCORRECT_MIN_WORD_LEN = 3
        private const val AUTOCORRECT_MAX_DISTANCE = 2.0
        private const val AUTOCORRECT_MIN_CONFIDENCE = 0.35f
        private const val SCORE_MARGIN_RATIO = 1.3
    }

    fun getAutocorrection(
        typedWord: String,
        candidates: List<PredictionEngine.Suggestion>,
        previousWord: String? = null,
        prev2: String? = null
    ): PredictionEngine.Suggestion? {
        val sanitized = WordSanitizer.sanitizeToken(typedWord)
        val cleaned = sanitized.cleanWord
        if (cleaned.length < AUTOCORRECT_MIN_WORD_LEN) return null
        if (WordSanitizer.isAutocorrectImmune(typedWord, cleaned)) return null

        val normCleaned = stripAccentsFn(cleaned.lowercase())

        // 1. Evaluar tildes y diacríticos
        val resolvedDiacritic = diacriticManager.resolveDiacriticCandidate(
            typedWord = cleaned,
            candidates = candidates,
            previousWord = previousWord,
            prev2 = prev2,
            patternLearningManager = patternLearningManager
        )

        if (diacriticManager.isDiacriticWord(cleaned)) {
            if (resolvedDiacritic != null) {
                if (resolvedDiacritic.text.lowercase() != cleaned.lowercase()) {
                    return resolvedDiacritic.copy(isCorrection = true)
                }
                return null
            }
        } else if (resolvedDiacritic != null && cleaned.lowercase() == normCleaned) {
            return resolvedDiacritic.copy(isCorrection = true)
        }

        // Si la palabra escrita ya es una palabra válida y conocida, no reemplazar automáticamente
        if (isKnownWordFn(cleaned)) return null

        // Filtrar solo verdaderas correcciones (excluir completaciones de prefijos incompletos)
        val corrections = candidates
            .filter { it.isCorrection }
            .sortedByDescending { it.score }

        if (corrections.isEmpty()) return null

        val best = corrections[0]
        val secondScore = corrections.getOrNull(1)?.score ?: 0.0

        // Verificar el margen del primer candidato frente al segundo
        if (secondScore > 0.0 && best.score < secondScore * SCORE_MARGIN_RATIO) {
            return null
        }

        val dist = weightedDistanceFn(cleaned.lowercase(), best.text.lowercase())
        if (dist > AUTOCORRECT_MAX_DISTANCE) return null
        if (best.confidence < AUTOCORRECT_MIN_CONFIDENCE) return null

        return best
    }
}
