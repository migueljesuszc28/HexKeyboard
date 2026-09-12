package com.example.hexkeyboard.data.repository

import android.content.Context
import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.text.Normalizer

@Serializable
data class EmojibaseItem(
    val unicode: String,
    val label: String,
    val hexcode: String? = null,
    val group: Int? = null,
    val subgroup: Int? = null,
    val order: Int? = null,
    val gender: Int? = null,
    val tags: List<String>? = null,
    val skins: List<EmojibaseItem>? = null,
    val emoticon: JsonEmoticon? = null
)

@Serializable(with = JsonEmoticonSerializer::class)
sealed class JsonEmoticon {
    @Serializable
    data class Single(val value: String) : JsonEmoticon()
    @Serializable
    data class Multiple(val values: List<String>) : JsonEmoticon()
}

object JsonEmoticonSerializer : KSerializer<JsonEmoticon> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    override fun serialize(encoder: Encoder, value: JsonEmoticon) {}
    override fun deserialize(decoder: Decoder): JsonEmoticon {
        val input = decoder as? JsonDecoder ?: throw Exception("Solo JSON")
        val element = input.decodeJsonElement()
        return if (element is JsonArray) {
            JsonEmoticon.Multiple(element.map { it.toString().replace("\"", "") })
        } else {
            JsonEmoticon.Single(element.toString().replace("\"", ""))
        }
    }
}

object EmojiProvider {
    data class EmojiCategory(
        val name: String,
        val icon: String,
        val emojis: List<String>
    )

    data class EmojiFamily(
        val neutral: String? = null,
        val male: String? = null,
        val female: String? = null,
        val allVariations: List<String> = emptyList()
    )

    sealed class EmojiGridItem {
        data class Header(val name: String, val categoryIndex: Int) : EmojiGridItem()
        data class Emoji(
            val code: String, 
            val category: String, 
            val categoryIndex: Int,
            val canonical: String,
            val family: EmojiFamily
        ) : EmojiGridItem()
    }

    private data class EmojiSearchEntry(
        val unicode: String,
        val normalizedLabel: String,
        val keywords: Set<String>,
        val order: Int
    )

    private var allEmojis: List<EmojibaseItem> = emptyList()
    private val emojiToFamily = mutableMapOf<String, EmojiFamily>()
    private var emojiMap: Map<String, EmojibaseItem> = emptyMap()
    private val searchEntries = mutableListOf<EmojiSearchEntry>()
    private val skinToneRegex = "[\uD83C\uDFFB-\uD83C\uDFFF]".toRegex()

    var categories: List<EmojiCategory> = emptyList()
        private set

    var flatGridItems: List<EmojiGridItem> = emptyList()
        private set

    val skinToneModifiers = listOf(
        "", "\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF"
    )

    private val groupNames = mapOf(
        0 to "Principales", 1 to "Personas", 3 to "Animales", 4 to "Comida",
        5 to "Lugares", 6 to "Actividades", 7 to "Objetos", 8 to "Símbolos", 9 to "Banderas"
    )

    private val categoryIcons = mapOf(
        "Principales" to "😀", "Personas" to "👋", "Animales" to "🐱",
        "Comida" to "🍏", "Actividades" to "⚽", "Lugares" to "🚗",
        "Objetos" to "💡", "Símbolos" to "🔣", "Banderas" to "🏳️"
    )

    private val countryAliases = mapOf(
        "US" to listOf("eeuu", "usa", "estados unidos", "america"),
        "ES" to listOf("espana", "españa", "spain"),
        "MX" to listOf("mexico", "méxico"),
        "AR" to listOf("argentina"),
        "CO" to listOf("colombia"),
        "CL" to listOf("chile"),
        "PE" to listOf("peru", "perú"),
        "VE" to listOf("venezuela"),
        "BR" to listOf("brasil", "brazil"),
        "EC" to listOf("ecuador"),
        "GT" to listOf("guatemala"),
        "CU" to listOf("cuba"),
        "PR" to listOf("puerto rico"),
        "UY" to listOf("uruguay"),
        "BO" to listOf("bolivia"),
        "PY" to listOf("paraguay"),
        "CR" to listOf("costa rica"),
        "PA" to listOf("panama", "panamá"),
        "DO" to listOf("dominicana", "republica dominicana", "república dominicana"),
        "HN" to listOf("honduras"),
        "NI" to listOf("nicaragua"),
        "SV" to listOf("el salvador", "salvador"),
        "CA" to listOf("canada", "canadá"),
        "FR" to listOf("francia", "france"),
        "DE" to listOf("alemania", "germany"),
        "IT" to listOf("italia", "italy"),
        "JP" to listOf("japon", "japón", "japan"),
        "CN" to listOf("china"),
        "GB" to listOf("inglaterra", "reino unido", "uk", "gran bretaña"),
        "RU" to listOf("rusia", "russia"),
        "KR" to listOf("corea", "corea del sur", "korea"),
        "KP" to listOf("corea del norte"),
        "UA" to listOf("ucrania", "ukraine"),
        "PT" to listOf("portugal"),
        "NL" to listOf("paises bajos", "países bajos", "holanda"),
        "BE" to listOf("belgica", "bélgica"),
        "CH" to listOf("suiza"),
        "AT" to listOf("austria"),
        "GR" to listOf("grecia"),
        "TR" to listOf("turquia", "turquía"),
        "MA" to listOf("marruecos"),
        "EG" to listOf("egipto"),
        "ZA" to listOf("sudafrica", "sudáfrica"),
        "AU" to listOf("australia"),
        "NZ" to listOf("nueva zelanda")
    )

    private val stopWords = setOf(
        "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas", "con", "en", "y", "o", "para", "por", "mi", "su"
    )

    fun normalizeText(text: String): String {
        if (text.isEmpty()) return ""
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        val noDiacritics = temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noDiacritics.lowercase()
            .replace(Regex("[^a-z0-9ñ\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    @Volatile
    private var isInitializing = false

    fun initialize(context: Context) {
        if (categories.isNotEmpty() || isInitializing) return
        
        synchronized(this) {
            if (categories.isNotEmpty() || isInitializing) return
            isInitializing = true
        }

        try {
            val jsonString = try {
                context.assets.open("emojis.json").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                val file = File("src/main/assets/emojis.json").takeIf { it.exists() }
                    ?: File("app/src/main/assets/emojis.json")
                file.readText()
            }
            val json = Json { ignoreUnknownKeys = true }
            val loadedEmojis = json.decodeFromString<List<EmojibaseItem>>(jsonString)
            emojiMap = loadedEmojis.associateBy { it.unicode }

            // FASE 1: Identificación estricta de variaciones (Skins)
            val variationUnicodes = mutableSetOf<String>()
            loadedEmojis.forEach { item ->
                item.skins?.forEach { skin -> 
                    variationUnicodes.add(skin.unicode)
                }
            }

            // FASE 2: Agrupación por Concepto (Familias)
            val baseEmojis = loadedEmojis.filter { !variationUnicodes.contains(it.unicode) }
            
            val familiesMap = baseEmojis.groupBy { item ->
                val hex = item.hexcode ?: ""
                val isSocialGroup = item.subgroup in listOf(15, 16, 17)
                val hasMultiplePeople = hex.split("-").count { it.startsWith("1F46") || it.startsWith("1F9D") } > 1

                if (isSocialGroup || hasMultiplePeople) {
                    "social_${item.unicode}"
                } else {
                    val isProfession = hex.contains("200D") && (hex.startsWith("1F468") || hex.startsWith("1F469") || hex.startsWith("1F9D1"))
                    val conceptHex = hex.replace("-FE0F", "")
                                       .replace(Regex("-200D-264[02]"), "")
                                       .replace(Regex("(1F468|1F469|1F9D1)-200D-"), "")
                    
                    if (isProfession) "profession_$conceptHex" else "family_$conceptHex"
                }
            }

            emojiToFamily.clear()
            val rootsToInclude = mutableSetOf<String>()

            familiesMap.forEach { (_, members) ->
                var neutral: String? = null
                var male: String? = null
                var female: String? = null

                members.forEach { m ->
                    val hex = m.hexcode ?: ""
                    when {
                        hex.contains("2642") || hex.startsWith("1F468") -> male = m.unicode
                        hex.contains("2640") || hex.startsWith("1F469") -> female = m.unicode
                        else -> neutral = m.unicode
                    }
                }

                val root = neutral ?: male ?: female ?: members.first().unicode
                rootsToInclude.add(root)

                val allVars = members.flatMap { m ->
                    listOf(m.unicode) + (m.skins?.map { it.unicode } ?: emptyList())
                }.distinct()

                val family = EmojiFamily(neutral, male, female, allVars)
                
                members.forEach { m ->
                    emojiToFamily[m.unicode] = family
                    m.skins?.forEach { skin -> emojiToFamily[skin.unicode] = family }
                }
            }

            // FASE 3: Construcción de Categorías e Índice de Búsqueda
            val categoryList = mutableListOf<EmojiCategory>()
            categoryList.add(EmojiCategory("Recientes", "🕒", emptyList()))

            searchEntries.clear()

            groupNames.forEach { (groupId, name) ->
                val emojisInGroup = loadedEmojis.filter { it.group == groupId }
                val filteredUnicodes = emojisInGroup
                    .filter { rootsToInclude.contains(it.unicode) }
                    .sortedWith(compareBy({ it.subgroup ?: 0 }, { it.order ?: 0 }))
                    .map { it.unicode }

                if (filteredUnicodes.isNotEmpty()) {
                    categoryList.add(EmojiCategory(name, categoryIcons[name] ?: "❓", filteredUnicodes))
                }
            }
            
            // FASE 4: Índice de Búsqueda Avanzado
            val targetUnicodes = (rootsToInclude + loadedEmojis.map { it.unicode }).distinct()
            
            targetUnicodes.forEach { unicode ->
                val item = emojiMap[unicode] ?: return@forEach
                val normLabel = normalizeText(item.label)
                val keywords = mutableSetOf<String>()

                normLabel.split(" ").filter { it.isNotEmpty() }.forEach { keywords.add(it) }

                item.tags?.forEach { tag ->
                    val normTag = normalizeText(tag)
                    normTag.split(" ").filter { it.isNotEmpty() }.forEach { keywords.add(it) }

                    val upperTag = tag.uppercase()
                    countryAliases[upperTag]?.forEach { alias ->
                        val normAlias = normalizeText(alias)
                        normAlias.split(" ").filter { it.isNotEmpty() }.forEach { keywords.add(it) }
                    }
                }

                if (normLabel.startsWith("bandera")) {
                    keywords.add("bandera")
                    keywords.add("banderas")
                    keywords.add("pais")
                    keywords.add("paises")
                    val countryPart = normLabel.removePrefix("bandera").trim()
                    if (countryPart.isNotEmpty()) {
                        countryPart.split(" ").filter { it.isNotEmpty() }.forEach { keywords.add(it) }
                    }
                }

                searchEntries.add(
                    EmojiSearchEntry(
                        unicode = unicode,
                        normalizedLabel = normLabel,
                        keywords = keywords,
                        order = item.order ?: 9999
                    )
                )
            }

            categories = categoryList
            
            // Pre-calcular lista plana para el grid
            val flatItems = mutableListOf<EmojiGridItem>()
            categoryList.forEachIndexed { index, category ->
                if (category.name != "Recientes") {
                    flatItems.add(EmojiGridItem.Header(category.name, index))
                    category.emojis.forEach { code ->
                        val canonical = getCanonicalEmoji(code)
                        val family = getEmojiFamily(canonical)
                        flatItems.add(EmojiGridItem.Emoji(code, category.name, index, canonical, family)) 
                    }
                }
            }
            flatGridItems = flatItems
            
            allEmojis = emptyList()
            
        } catch (e: Exception) {
            Log.e("EmojiProvider", "Error cargando emojis: ${e.message}", e)
            categories = listOf(EmojiCategory("Recientes", "🕒", emptyList()))
        } finally {
            synchronized(this) {
                isInitializing = false
            }
        }
    }

    fun getEmojiFamily(emoji: String): EmojiFamily = emojiToFamily[emoji] ?: EmojiFamily(neutral = emoji, allVariations = listOf(emoji))

    fun getCanonicalEmoji(emoji: String): String {
        return getEmojiFamily(emoji.replace(skinToneRegex, "")).neutral ?: emoji.replace(skinToneRegex, "")
    }

    fun getGenderedVariant(emoji: String, genderIndex: Int): String {
        val family = getEmojiFamily(emoji.replace(skinToneRegex, ""))
        return when (genderIndex) {
            1 -> family.male ?: family.neutral ?: family.female
            2 -> family.female ?: family.neutral ?: family.male
            else -> family.neutral ?: family.male ?: family.female
        } ?: emoji
    }

    fun getEmojiVariationGrid(emoji: String): List<List<String>> {
        val family = getEmojiFamily(emoji)
        val roots = listOfNotNull(family.neutral, family.male, family.female).distinct()
        
        val grid = roots.map { root ->
            skinToneModifiers.map { modifier ->
                applySkinTone(root, modifier)
            }.distinct()
        }.filter { it.isNotEmpty() }

        if (grid.size == 1 && grid[0].size == 1) return emptyList()
        
        return grid
    }

    fun hasVariations(emoji: String): Boolean {
        val grid = getEmojiVariationGrid(emoji)
        if (grid.isEmpty()) return false
        val uniqueCount = grid.flatten().distinct().size
        return uniqueCount > 1
    }

    fun getEmojiVariations(emoji: String): List<String> = getEmojiFamily(emoji).allVariations

    fun supportsSkinTone(emoji: String): Boolean {
        val family = emojiToFamily[emoji]
        if (family != null) {
            return family.allVariations.any { variant -> emojiMap[variant]?.skins?.isNotEmpty() == true }
        }
        return emojiMap[emoji.replace(skinToneRegex, "")]?.skins?.isNotEmpty() == true
    }

    fun applySkinTone(emoji: String, modifier: String): String {
        val baseEmoji = emoji.replace(skinToneRegex, "")
        if (modifier.isEmpty()) return baseEmoji

        val item = emojiMap[baseEmoji]
        if (item?.skins != null) {
            val skinEmoji = item.skins.find { it.unicode.contains(modifier) }
            if (skinEmoji != null) return skinEmoji.unicode
        }

        val family = emojiToFamily[baseEmoji]
        if (family != null) {
            for (variant in family.allVariations) {
                if (variant.contains(modifier) && variant.replace(skinToneRegex, "") == baseEmoji) return variant
            }
        }
        return emoji
    }

    fun searchEmojis(query: String): List<String> {
        val normQuery = normalizeText(query)
        if (normQuery.isEmpty()) return emptyList()

        val allTokens = normQuery.split(" ").filter { it.isNotEmpty() }
        if (allTokens.isEmpty()) return emptyList()

        val tokens = if (allTokens.size > 1) {
            val filtered = allTokens.filter { !stopWords.contains(it) }
            if (filtered.isNotEmpty()) filtered else allTokens
        } else {
            allTokens
        }

        data class ScoredEmoji(val unicode: String, val score: Int, val order: Int)

        val scoredResults = mutableListOf<ScoredEmoji>()

        for (entry in searchEntries) {
            var score = 0

            if (entry.normalizedLabel == normQuery) {
                score += 300
            } else if (entry.normalizedLabel.startsWith(normQuery)) {
                score += 200
            } else if (entry.normalizedLabel.contains(normQuery)) {
                score += 120
            }

            var matchedTokenCount = 0

            for (token in tokens) {
                var tokenMatched = false

                for (kw in entry.keywords) {
                    if (kw == token) {
                        score += 80
                        tokenMatched = true
                        break
                    } else if (kw.startsWith(token)) {
                        score += 50
                        tokenMatched = true
                        break
                    } else if (kw.contains(token)) {
                        score += 25
                        tokenMatched = true
                        break
                    }
                }

                if (tokenMatched) {
                    matchedTokenCount++
                }
            }

            if (matchedTokenCount == tokens.size) {
                score += 100
            }

            if (score > 0) {
                scoredResults.add(ScoredEmoji(entry.unicode, score, entry.order))
            }
        }

        return scoredResults
            .sortedWith(compareByDescending<ScoredEmoji> { it.score }.thenBy { it.order })
            .map { it.unicode }
            .distinct()
    }
}
