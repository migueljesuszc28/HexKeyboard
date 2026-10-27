package com.example.hexkeyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.hexkeyboard.data.repository.EmojiProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmojiSearchTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        EmojiProvider.initialize(context)
    }

    @Test
    fun testNormalizeText() {
        println("Categories count: ${EmojiProvider.categories.size}")
        assertEquals("mexico", EmojiProvider.normalizeText("México"))
        assertEquals("espana", EmojiProvider.normalizeText("España"))
        assertEquals("peru", EmojiProvider.normalizeText("Perú"))
        assertEquals("bandera espana", EmojiProvider.normalizeText("Bandera: España"))
    }

    @Test
    fun testSearchGorila() {
        val results = EmojiProvider.searchEmojis("gorila")
        assertTrue("Debe encontrar el emoji de gorila", results.contains("🦍"))
    }

    @Test
    @Ignore("Omitido por solicitud del usuario")
    fun testSearchFlagsByCountryName() {
        val mexicoResults = EmojiProvider.searchEmojis("mexico")
        assertTrue("Búsqueda 'mexico' debe retornar la bandera de México 🇲🇽", mexicoResults.contains("🇲🇽"))
    }

    @Test
    fun testSearchGeneralTerms() {
        val corazonResults = EmojiProvider.searchEmojis("corazon")
        assertTrue("Búsqueda 'corazon' sin tilde debe retornar corazón", corazonResults.any { it.contains("❤") })

        val perroResults = EmojiProvider.searchEmojis("perro")
        assertTrue("Búsqueda 'perro' debe retornar emoji de perro 🐶", perroResults.contains("🐶"))

        val gatoResults = EmojiProvider.searchEmojis("gato")
        assertTrue("Búsqueda 'gato' debe retornar emoji de gato 🐱", gatoResults.contains("🐱"))

        val pizzaResults = EmojiProvider.searchEmojis("pizza")
        assertTrue("Búsqueda 'pizza' debe retornar emoji de pizza 🍕", pizzaResults.contains("🍕"))
    }
}
