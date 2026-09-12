package com.example.hexkeyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.hexkeyboard.data.repository.EmojiProvider
import org.junit.Assert.*
import org.junit.Before
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
    fun testSearchFlagsByCountryName() {
        val mexicoResults = EmojiProvider.searchEmojis("mexico")
        assertTrue("Búsqueda 'mexico' debe retornar la bandera de México 🇲🇽", mexicoResults.contains("🇲🇽"))

        val espanaResults = EmojiProvider.searchEmojis("espana")
        assertTrue("Búsqueda 'espana' debe retornar la bandera de España 🇪🇸", espanaResults.contains("🇪🇸"))

        val argentinaResults = EmojiProvider.searchEmojis("argentina")
        assertTrue("Búsqueda 'argentina' debe retornar la bandera de Argentina 🇦🇷", argentinaResults.contains("🇦🇷"))

        val colombiaResults = EmojiProvider.searchEmojis("colombia")
        assertTrue("Búsqueda 'colombia' debe retornar la bandera de Colombia 🇨🇴", colombiaResults.contains("🇨🇴"))

        val eeuuResults = EmojiProvider.searchEmojis("eeuu")
        assertTrue("Búsqueda 'eeuu' debe retornar la bandera de EE.UU. 🇺🇸", eeuuResults.contains("🇺🇸"))

        val venezuelaResults = EmojiProvider.searchEmojis("venezuela")
        assertTrue("Búsqueda 'venezuela' debe retornar la bandera de Venezuela 🇻🇪", venezuelaResults.contains("🇻🇪"))

        val chileResults = EmojiProvider.searchEmojis("chile")
        assertTrue("Búsqueda 'chile' debe retornar la bandera de Chile 🇨🇱", chileResults.contains("🇨🇱"))

        val ukResults = EmojiProvider.searchEmojis("uk")
        assertTrue("Búsqueda 'uk' debe retornar la bandera del Reino Unido 🇬🇧", ukResults.contains("🇬🇧"))
    }

    @Test
    fun testSearchGeneralTerms() {
        val banderaResults = EmojiProvider.searchEmojis("bandera")
        assertTrue("Búsqueda 'bandera' debe retornar banderas", banderaResults.contains("🇲🇽") && banderaResults.contains("🇪🇸"))

        val corazonResults = EmojiProvider.searchEmojis("corazon")
        assertTrue("Búsqueda 'corazon' sin tilde debe retornar corazón ❤️", corazonResults.contains("❤️"))

        val perroResults = EmojiProvider.searchEmojis("perro")
        assertTrue("Búsqueda 'perro' debe retornar emoji de perro 🐶", perroResults.contains("🐶"))

        val gatoResults = EmojiProvider.searchEmojis("gato")
        assertTrue("Búsqueda 'gato' debe retornar emoji de gato 🐱", gatoResults.contains("🐱"))

        val pizzaResults = EmojiProvider.searchEmojis("pizza")
        assertTrue("Búsqueda 'pizza' debe retornar emoji de pizza 🍕", pizzaResults.contains("🍕"))
    }
}
