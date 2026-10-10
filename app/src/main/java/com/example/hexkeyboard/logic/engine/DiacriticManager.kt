package com.example.hexkeyboard.logic.engine

/**
 * Gestor de desambiguación sintáctica y gramatical de tildes/acentos diacríticos.
 * Distingue entre palabras con tilde incondicional (ej. "canción", "árbol") y
 * pares diacríticos con doble validez según contexto (ej. "el/él", "esta/está", "si/sí").
 */
class DiacriticManager {

    companion object {
        // Pares diacríticos en español: FormaSinTilde -> FormaConTilde
        private val DIACRITIC_PAIRS = mapOf(
            "el" to "él",
            "tu" to "tú",
            "si" to "sí",
            "se" to "sé",
            "mas" to "más",
            "te" to "té",
            "esta" to "está",
            "este" to "esté",
            "como" to "cómo",
            "que" to "qué",
            "donde" to "dónde",
            "cuando" to "cuándo",
            "quien" to "quién",
            "solo" to "sólo"
        )

        private val REVERSE_DIACRITIC_PAIRS = DIACRITIC_PAIRS.entries.associate { (k, v) -> v to k }
    }

    /**
     * Determina si una palabra pertenece a un par diacrítico.
     */
    fun isDiacriticWord(word: String): Boolean {
        val low = word.lowercase()
        return DIACRITIC_PAIRS.containsKey(low) || REVERSE_DIACRITIC_PAIRS.containsKey(low)
    }

    /**
     * Evalúa el contexto previo para decidir si una palabra debe llevar tilde o no.
     * Para pares diacríticos, consulta al modelo de lenguaje N-Grama.
     * Para palabras con tilde incondicional, promueve la versión correctamente acentuada.
     */
    fun resolveDiacriticCandidate(
        typedWord: String,
        candidates: List<PredictionEngine.Suggestion>,
        previousWord: String?,
        prev2: String?,
        patternLearningManager: PatternLearningManager?
    ): PredictionEngine.Suggestion? {
        if (candidates.isEmpty()) return null

        val lowTyped = typedWord.lowercase()
        val normTyped = PredictionEngine.stripAccentsStatic(lowTyped)

        // 1. Manejo de Pares Diacríticos (ej. el/él, esta/está, si/sí)
        val accentedPair = DIACRITIC_PAIRS[normTyped]
        if (accentedPair != null) {
            val unaccentedCandidate = candidates.find { it.text.lowercase() == normTyped }
            val accentedCandidate = candidates.find { it.text.lowercase() == accentedPair }

            if (unaccentedCandidate != null && accentedCandidate != null) {
                val scoreUnaccented = patternLearningManager?.calculateContextScore(normTyped, previousWord, prev2) ?: 1.0
                val scoreAccented = patternLearningManager?.calculateContextScore(accentedPair, previousWord, prev2) ?: 1.0

                // Si hay evidencia clara de N-gramas para la versión acentuada, usarla;
                // de lo contrario, mantener la versión sin tilde por defecto gramatical.
                return if (scoreAccented > scoreUnaccented * 1.3) {
                    accentedCandidate
                } else {
                    unaccentedCandidate
                }
            } else if (unaccentedCandidate != null) {
                return unaccentedCandidate
            }
        }

        // 2. Manejo de Tildes Incondicionales (ej. cancion -> canción, tambien -> también)
        // Se busca una versión acentuada oficial cuyo texto sin acentos coincida con la palabra ingresada
        val bestInconditionalAccent = candidates.find { candidate ->
            val normCandidate = PredictionEngine.stripAccentsStatic(candidate.text.lowercase())
            normCandidate == normTyped && candidate.text.lowercase() != normTyped
        }

        return bestInconditionalAccent
    }
}
