package com.example.hexkeyboard.logic.engine

/**
 * Utilidad de sanitización de texto e inmunidad de autocorrección.
 * Aísla los signos de puntuación de las palabras léxicas para evitar que la
 * autocorrección o la búsqueda en diccionarios borren o alteren comas, puntos
 * o formatos especiales (emails, URLs, acrónimos).
 */
object WordSanitizer {

    data class SanitizedToken(
        val rawToken: String,
        val cleanWord: String,
        val leadingPunctuation: String = "",
        val trailingPunctuation: String = ""
    ) {
        val hasTrailingPunctuation: Boolean
            get() = trailingPunctuation.isNotEmpty()
    }

    private val PUNCTUATION_SET = setOf(
        ',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}', '«', '»', '—', '-'
    )

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val URL_REGEX = Regex("^(https?://|www\\.)[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*$")

    /**
     * Desinfecta un token de entrada separando los signos de puntuación iniciales y finales
     * de la palabra limpia principal.
     */
    fun sanitizeToken(rawToken: String): SanitizedToken {
        if (rawToken.isEmpty()) return SanitizedToken("", "")

        // Si es un email o URL directo, lo conservamos entero sin quitar puntos
        if (EMAIL_REGEX.matches(rawToken) || URL_REGEX.matches(rawToken)) {
            return SanitizedToken(rawToken, rawToken)
        }

        var start = 0
        var end = rawToken.length - 1

        while (start < rawToken.length && rawToken[start] in PUNCTUATION_SET) {
            start++
        }

        while (end >= start && rawToken[end] in PUNCTUATION_SET) {
            end--
        }

        if (start > end) {
            return SanitizedToken(rawToken, "", leadingPunctuation = rawToken)
        }

        val leading = rawToken.substring(0, start)
        val clean = rawToken.substring(start, end + 1)
        val trailing = rawToken.substring(end + 1)

        return SanitizedToken(
            rawToken = rawToken,
            cleanWord = clean,
            leadingPunctuation = leading,
            trailingPunctuation = trailing
        )
    }

    /**
     * Determina si una palabra es inmune a la autocorrección (evita alterar acrónimos,
     * formatos especiales, números o palabras con puntuación explícita).
     */
    fun isAutocorrectImmune(rawToken: String, cleanWord: String): Boolean {
        if (cleanWord.length < 2) return true

        // 1. Inmune si el usuario ya escribió signos de puntuación finales (ej. "hola,")
        if (rawToken.endsWith(",") || rawToken.endsWith(".") || rawToken.endsWith("!") ||
            rawToken.endsWith("?") || rawToken.endsWith(";") || rawToken.endsWith(":")
        ) {
            return true
        }

        // 2. Mentions y Hashtags (@usuario, #tema)
        if (rawToken.startsWith("@") || rawToken.startsWith("#")) return true

        // 3. Emails y URLs
        if (EMAIL_REGEX.matches(rawToken) || URL_REGEX.matches(rawToken)) return true

        // 4. Números o palabras que contienen dígitos (ej. "iPhone13", "123")
        if (cleanWord.any { it.isDigit() }) return true

        // 5. Acrónimos y siglas en mayúsculas sostenidas de 2+ letras (ej. "GPS", "API", "USA", "JSON")
        if (cleanWord.all { it.isUpperCase() }) return true

        return false
    }
}
