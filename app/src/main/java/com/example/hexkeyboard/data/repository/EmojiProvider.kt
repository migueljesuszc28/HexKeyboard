package com.example.hexkeyboard.data.repository

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement

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

    private var allEmojis: List<EmojibaseItem> = emptyList()
    private val emojiToFamily = mutableMapOf<String, EmojiFamily>()
    private var emojiMap: Map<String, EmojibaseItem> = emptyMap()
    private val keywordIndex = mutableMapOf<String, MutableSet<String>>()
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

    @Volatile
    private var isInitializing = false

    fun initialize(context: Context) {
        if (categories.isNotEmpty() || isInitializing) return
        
        synchronized(this) {
            if (categories.isNotEmpty() || isInitializing) return
            isInitializing = true
        }

        try {
            val jsonString = context.assets.open("emojis.json").bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true }
            val loadedEmojis = json.decodeFromString<List<EmojibaseItem>>(jsonString)
            emojiMap = loadedEmojis.associateBy { it.unicode }

            // FASE 1: Identificación estricta de variaciones (Skins)
            // Coleccionamos TODOS los unicodes que Emojibase define como variantes de otro
            val variationUnicodes = mutableSetOf<String>()
            loadedEmojis.forEach { item ->
                item.skins?.forEach { skin -> 
                    variationUnicodes.add(skin.unicode)
                }
            }

            // FASE 2: Agrupación por Concepto (Familias)
            // Solo procesamos los que NO son variaciones de tono de piel ya identificadas
            val baseEmojis = loadedEmojis.filter { !variationUnicodes.contains(it.unicode) }
            
            val familiesMap = baseEmojis.groupBy { item ->
                val hex = item.hexcode ?: ""
                
                // Los grupos/subgrupos de Familias y Parejas NO deben reducirse a un concepto
                // porque contienen múltiples personas y causarían colisiones (ej. colisionar con "niño")
                val isSocialGroup = item.subgroup in listOf(15, 16, 17) // family, person-social, person-sport (algunos casos)
                val hasMultiplePeople = hex.split("-").count { it.startsWith("1F46") || it.startsWith("1F9D") } > 1

                if (isSocialGroup || hasMultiplePeople) {
                    "social_${item.unicode}" // Mantener como elemento único
                } else {
                    // Identificar si es una profesión o rol basado en una persona (ej. Piloto = Persona + Avión)
                    val isProfession = hex.contains("200D") && (hex.startsWith("1F468") || hex.startsWith("1F469") || hex.startsWith("1F9D1"))

                    // Normalización agresiva solo para profesiones y actividades individuales
                    val conceptHex = hex.replace("-FE0F", "")
                                       .replace(Regex("-200D-264[02]"), "")
                                       .replace(Regex("(1F468|1F469|1F9D1)-200D-"), "")
                    
                    // Usar namespaces diferentes para evitar que el objeto solo (ej. Cohete) 
                    // colisione con la profesión que lo usa (ej. Astronauta)
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
                    // Identificación de género basada en el estándar ZWJ de Emojibase
                    when {
                        hex.contains("2642") || hex.startsWith("1F468") -> male = m.unicode
                        hex.contains("2640") || hex.startsWith("1F469") -> female = m.unicode
                        else -> neutral = m.unicode
                    }
                }

                // El representante principal (Root) es el neutral, o el primero que encontremos
                val root = neutral ?: male ?: female ?: members.first().unicode
                rootsToInclude.add(root)

                // Construir la familia con todas las variaciones posibles (incluyendo skins de cada miembro)
                val allVars = members.flatMap { m ->
                    listOf(m.unicode) + (m.skins?.map { it.unicode } ?: emptyList())
                }.distinct()

                val family = EmojiFamily(neutral, male, female, allVars)
                
                // Mapear cada miembro (y sus skins) a esta familia única
                members.forEach { m ->
                    emojiToFamily[m.unicode] = family
                    m.skins?.forEach { skin -> emojiToFamily[skin.unicode] = family }
                }
            }

            // FASE 3: Construcción de Categorías e Índice de Búsqueda
            val categoryList = mutableListOf<EmojiCategory>()
            categoryList.add(EmojiCategory("Recientes", "🕒", emptyList()))

            // Reiniciar índice
            keywordIndex.clear()

            groupNames.forEach { (groupId, name) ->
                val emojisInGroup = loadedEmojis.filter { it.group == groupId }
                
                // Solo incluir en el panel principal los que marcamos como Roots.
                // Ordenamos primero por subgrupo (ej: manos, partes del cuerpo, roles) 
                // y luego por su orden interno para una navegación lógica estilo Gboard.
                val filteredUnicodes = emojisInGroup
                    .filter { rootsToInclude.contains(it.unicode) }
                    .sortedWith(compareBy({ it.subgroup ?: 0 }, { it.order ?: 0 }))
                    .map { it.unicode }

                if (filteredUnicodes.isNotEmpty()) {
                    categoryList.add(EmojiCategory(name, categoryIcons[name] ?: "❓", filteredUnicodes))
                }
            }
            
            // Llenar el índice de búsqueda con las familias para evitar resultados duplicados
            rootsToInclude.forEach { rootUnicode ->
                val item = emojiMap[rootUnicode] ?: return@forEach
                val keywords = mutableSetOf<String>()
                keywords.add(item.label.lowercase())
                item.tags?.forEach { keywords.add(it.lowercase()) }
                
                keywords.forEach { kw ->
                    kw.split(" ", "-", "_").forEach { word ->
                        if (word.length >= 2) {
                            keywordIndex.getOrPut(word) { mutableSetOf() }.add(rootUnicode)
                        }
                    }
                }
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
            
            allEmojis = emptyList() // Liberar memoria
            
        } catch (e: Exception) {
            e.printStackTrace()
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

    /**
     * Devuelve la variante de género correspondiente (0=Neutro, 1=Masculino, 2=Femenino)
     * para un emoji base.
     */
    fun getGenderedVariant(emoji: String, genderIndex: Int): String {
        val family = getEmojiFamily(emoji.replace(skinToneRegex, ""))
        return when (genderIndex) {
            1 -> family.male ?: family.neutral ?: family.female
            2 -> family.female ?: family.neutral ?: family.male
            else -> family.neutral ?: family.male ?: family.female
        } ?: emoji
    }

    /**
     * Devuelve las variaciones organizadas en filas (por género/rol) y columnas (por tono de piel).
     * Solo incluye variaciones que sean realmente distintas al emoji base.
     */
    fun getEmojiVariationGrid(emoji: String): List<List<String>> {
        val family = getEmojiFamily(emoji)
        val roots = listOfNotNull(family.neutral, family.male, family.female).distinct()
        
        val grid = roots.map { root ->
            skinToneModifiers.map { modifier ->
                applySkinTone(root, modifier)
            }.distinct() // Eliminar duplicados en la fila (si no soporta tonos de piel)
        }.filter { it.isNotEmpty() }

        // Si el resultado es solo una celda igual al emoji original, no hay variaciones reales
        if (grid.size == 1 && grid[0].size == 1) return emptyList()
        
        return grid
    }

    fun hasVariations(emoji: String): Boolean {
        val grid = getEmojiVariationGrid(emoji)
        if (grid.isEmpty()) return false
        
        // Contar cuántos emojis únicos hay en total en el grid
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
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        
        // Búsqueda Indexada (O(1) por palabra) en lugar de búsqueda lineal
        val queryWords = q.split(" ", "-", "_").filter { it.length >= 2 }
        if (queryWords.isEmpty()) return emptyList()

        var results: Set<String>? = null
        queryWords.forEach { word ->
            val matches = keywordIndex[word] ?: emptySet()
            results = if (results == null) matches else results!!.intersect(matches)
        }
        
        return results?.toList() ?: emptyList()
    }
}