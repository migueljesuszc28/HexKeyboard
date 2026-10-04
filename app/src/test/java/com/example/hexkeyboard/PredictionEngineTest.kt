package com.example.hexkeyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.hexkeyboard.logic.engine.PredictionEngine
import com.example.hexkeyboard.logic.engine.WordSanitizer
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
        // Cuando la palabra ya tiene tilde, getAutocorrection NO debe borrarla ni degradarla
        val correctionCancionAccented = predictionEngine.getAutocorrection("canción")
        assertNull("No debe corregir una palabra que ya posee tilde ('canción')", correctionCancionAccented)

        val correctionCancionWithDot = predictionEngine.getAutocorrection("canción.")
        assertNull("No debe corregir una palabra con punto que ya posee tilde ('canción.')", correctionCancionWithDot)

        val correctionTambienAccented = predictionEngine.getAutocorrection("también")
        assertNull("No debe corregir una palabra que ya posee tilde ('también')", correctionTambienAccented)
    }

    @Test
    fun testLearnFromInputDoesNotPolluteWithUnaccentedWord() {
        // Al aprender 'cancion', el motor debe aprender 'canción'
        predictionEngine.learnFromInput("cancion")
        predictionEngine.learnFromInput("cancion")
        predictionEngine.learnFromInput("cancion")

        // La autocorrección de 'cancion' debe seguir devolviendo 'canción'
        val correction = predictionEngine.getAutocorrection("cancion")
        assertNotNull("Autocorrección para 'cancion' debe seguir funcionando", correction)
        assertEquals("canción", correction?.text)
    }
}
