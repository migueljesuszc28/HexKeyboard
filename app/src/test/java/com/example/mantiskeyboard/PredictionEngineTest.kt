package com.example.mantiskeyboard

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.mantiskeyboard.logic.engine.PredictionEngine
import com.example.mantiskeyboard.logic.engine.TouchPoint
import com.example.mantiskeyboard.logic.engine.WordSanitizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PredictionEngineTest {

    private lateinit var predictionEngine: PredictionEngine

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        predictionEngine = PredictionEngine(context)
        predictionEngine.initialize("es")
    }

    @Test
    fun testWordSanitizerTrailingPunctuationNotImmune() {
        val sanitized = WordSanitizer.sanitizeToken("cancion.")
        assertEquals("cancion", sanitized.cleanWord)
        assertEquals(".", sanitized.trailingPunctuation)

        val isImmune = WordSanitizer.isAutocorrectImmune("cancion.", sanitized.cleanWord)
        assertFalse("La palabra 'cancion.' no debe ser inmune solo por tener un punto final", isImmune)
    }

    @Test
    fun testAutocorrectAccents() {
        val correctionCancion = predictionEngine.getAutocorrection("cancion")
        assertNotNull("Debe ofrecer autocorrección para 'cancion'", correctionCancion)
        assertEquals("canción", correctionCancion?.text)

        val correctionTambien = predictionEngine.getAutocorrection("tambien")
        assertNotNull("Debe ofrecer autocorrección para 'tambien'", correctionTambien)
        assertEquals("también", correctionTambien?.text)

        val correctionPagina = predictionEngine.getAutocorrection("pagina")
        assertNotNull("Debe ofrecer autocorrección para 'pagina'", correctionPagina)
        assertEquals("página", correctionPagina?.text)
    }

    @Test
    fun testAlreadyAccentedWordIsNotDegraded() {
        val correctionCancionAccented = predictionEngine.getAutocorrection("canción")
        assertNull("No debe corregir una palabra que ya posee tilde ('canción')", correctionCancionAccented)

        val correctionCancionWithDot = predictionEngine.getAutocorrection("canción.")
        assertNull("No debe corregir una palabra con punto que ya posee tilde ('canción.')", correctionCancionWithDot)

        val correctionTambienAccented = predictionEngine.getAutocorrection("también")
        assertNull("No debe corregir una palabra que ya posee tilde ('también')", correctionTambienAccented)
    }

    @Test
    fun testLearnFromInputDoesNotPolluteWithUnaccentedWord() {
        predictionEngine.learnFromInput("cancion")
        predictionEngine.learnFromInput("cancion")
        predictionEngine.learnFromInput("cancion")

        val correction = predictionEngine.getAutocorrection("cancion")
        assertNotNull("Autocorrección para 'cancion' debe seguir funcionando", correction)
        assertEquals("canción", correction?.text)
    }

    @Test
    fun testIncompletePrefixNotAutoCorrected() {
        // Un prefijo incompleto como 'hol' NO debe ser reemplazado por autocorrección
        val correction = predictionEngine.getAutocorrection("hol")
        assertNull("Prefijo incompleto 'hol' no debe ser sustituido por autocorrección", correction)
    }

    @Test
    fun testUserDictionaryPersistenceAndRetrieval() = runBlocking {
        predictionEngine.addUserWord("mantiskeyboardtest")
        assertTrue("La palabra agregada debe ser reconocida como conocida", predictionEngine.isKnownWord("mantiskeyboardtest"))

        // Re-inicializar para simular reinicio de sesión y carga desde disco
        predictionEngine.initialize("es", forceUserDictReload = true)
        assertTrue("La palabra persistida debe ser recuperada tras reiniciar", predictionEngine.isKnownWord("mantiskeyboardtest"))
        
        predictionEngine.removeUserWord("mantiskeyboardtest")
        assertFalse("La palabra eliminada no debe estar presente", predictionEngine.isKnownWord("mantiskeyboardtest"))
    }

    @Test
    fun testBeamSearchSpatialReranking() {
        val keyCenters = mapOf(
            'h' to PointF(100f, 100f),
            'o' to PointF(200f, 100f),
            'l' to PointF(300f, 100f),
            'a' to PointF(400f, 100f)
        )
        predictionEngine.updateKeyCenters(keyCenters)

        val touchPoints = listOf(
            TouchPoint('h', 101f, 99f),
            TouchPoint('o', 202f, 101f),
            TouchPoint('l', 298f, 100f),
            TouchPoint('a', 401f, 99f)
        )

        val suggestions = predictionEngine.getSuggestions("hola", touchPoints = touchPoints, limit = 5)
        assertTrue("Debe retornar sugerencias con re-ranking", suggestions.isNotEmpty())
        assertEquals("hola", suggestions.first().text.lowercase())
    }

    @Test
    fun testBinaryBigramPredictions() {
        val bigrams = predictionEngine.getBinaryBigramPredictions("de", limit = 5)
        assertNotNull("La lista de predicciones de bigramas no debe ser nula", bigrams)
    }
}
