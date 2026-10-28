package com.example.mantiskeyboard.logic.engine

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Encapsula la carga, mapeo en memoria y consultas sobre los archivos de
 * diccionario binario comprimidos en formato AOSP LatinIME v2 (.dict).
 */
class BinaryDictionary {

    data class LanguageBuffer(
        val langCode: String,
        val buffer: ByteBuffer,
        val headerSize: Int,
        val isPrimary: Boolean
    )

    data class ParsedNode(
        val word: String,
        val isTerminal: Boolean,
        val frequency: Int,
        val childrenAddr: Int?,
        val addressBase: Int
    )

    @Volatile
    var activeBuffers: List<LanguageBuffer> = emptyList()
        private set

    fun loadDictionaries(context: Context, primaryLang: String, secondaryLangs: List<String>) {
        val list = mutableListOf<LanguageBuffer>()

        loadDictionary(context, primaryLang, isPrimary = true)?.let { list.add(it) }

        secondaryLangs.filter { it != primaryLang }.forEach { secLang ->
            loadDictionary(context, secLang, isPrimary = false)?.let { list.add(it) }
        }

        activeBuffers = list
    }

    private fun loadDictionary(context: Context, lang: String, isPrimary: Boolean): LanguageBuffer? {
        val fileName = "main_$lang.dict"
        return try {
            val afd = context.assets.openFd(fileName)
            val fileChannel = afd.createInputStream().channel
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
            buffer.order(ByteOrder.BIG_ENDIAN)

            val magic = buffer.int
            if (magic != 0x9BC13AFE.toInt()) {
                Log.e("BinaryDictionary", "Magic inválido para '$lang': ${Integer.toHexString(magic)}")
                return null
            }

            buffer.short // version
            buffer.short // flags
            val headerSize = buffer.int

            Log.d("BinaryDictionary", "Diccionario base '$lang' (primario=$isPrimary) mapeado en memoria")
            LanguageBuffer(langCode = lang, buffer = buffer, headerSize = headerSize, isPrimary = isPrimary)
        } catch (_: Exception) {
            // Fallback a lectura estándar si el asset está comprimido en el APK
            try {
                val bytes = context.assets.open(fileName).use { it.readBytes() }
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val magic = buffer.int
                if (magic != 0x9BC13AFE.toInt()) return null
                buffer.short
                buffer.short
                val headerSize = buffer.int
                LanguageBuffer(langCode = lang, buffer = buffer, headerSize = headerSize, isPrimary = isPrimary)
            } catch (e: Exception) {
                Log.e("BinaryDictionary", "Error cargando diccionario base '$lang': ${e.message}")
                null
            }
        }
    }

    fun readChar(buffer: ByteBuffer): Int? {
        if (!buffer.hasRemaining()) return null
        val b = buffer.get().toInt() and 0xFF
        if (b == 0x1F) return null
        if (b < 0x20) {
            if (buffer.remaining() < 2) return null
            val next = buffer.short.toInt() and 0xFFFF
            return (b shl 16) or next
        }
        return b
    }

    fun readPtNodeCount(buffer: ByteBuffer): Int {
        if (!buffer.hasRemaining()) return 0
        val msb = buffer.get().toInt() and 0xFF
        return if (msb <= 0x7F) msb else {
            if (!buffer.hasRemaining()) return 0
            ((0x7F and msb) shl 8) + (buffer.get().toInt() and 0xFF)
        }
    }

    fun parseNextNode(buffer: ByteBuffer, prefix: String): ParsedNode {
        val flags = buffer.get().toInt() and 0xFF
        val addrType = flags and 0xC0
        val hasMultipleChars = (flags and 0x20) != 0
        val isTerminal = (flags and 0x10) != 0
        val hasShortcuts = (flags and 0x08) != 0
        val hasBigrams = (flags and 0x04) != 0

        val sb = StringBuilder()
        if (hasMultipleChars) {
            while (true) {
                val c = readChar(buffer) ?: break
                sb.appendCodePoint(c)
            }
        } else {
            readChar(buffer)?.let { sb.appendCodePoint(it) }
        }
        val fullWord = prefix + sb.toString()

        var freq = -1
        if (isTerminal) {
            freq = if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else 0
        }

        val addressBase = buffer.position()
        val childrenAddr: Int? = when (addrType) {
            0x00 -> null
            0x40 -> if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else null
            0x80 -> if (buffer.remaining() >= 2) buffer.short.toInt() and 0xFFFF else null
            else -> {
                if (buffer.remaining() >= 3) {
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt() and 0xFF
                    val b3 = buffer.get().toInt() and 0xFF
                    (b1 shl 16) or (b2 shl 8) or b3
                } else null
            }
        }

        if (isTerminal && hasShortcuts && buffer.remaining() >= 2) {
            val size = buffer.short.toInt() and 0xFFFF
            val targetPos = buffer.position() + size - 2
            if (targetPos in 0..buffer.limit()) {
                buffer.position(targetPos)
            }
        }

        if (isTerminal && hasBigrams) {
            while (buffer.hasRemaining()) {
                val bFlags = buffer.get().toInt() and 0xFF
                val bAddrType = bFlags and 0x30
                when (bAddrType) {
                    0x10 -> if (buffer.hasRemaining()) buffer.get()
                    0x20 -> if (buffer.remaining() >= 2) buffer.short
                    0x30 -> {
                        if (buffer.remaining() >= 3) {
                            buffer.get()
                            buffer.short
                        }
                    }
                }
                if ((bFlags and 0x80) == 0) break
            }
        }

        return ParsedNode(fullWord, isTerminal, freq, childrenAddr, addressBase)
    }

    /**
     * Resuelve predicciones de bigramas leyendo la tabla de bigramas
     * y buscando la palabra objetivo en la dirección apuntada.
     */
    fun getBinaryBigramPredictions(
        previousWord: String,
        limit: Int = 5,
        stripAccentsFn: (String) -> String
    ): List<Pair<String, Double>> {
        val cleanPrev = previousWord.trim().lowercase()
        if (cleanPrev.length < 2) return emptyList()

        val results = mutableListOf<Pair<String, Double>>()

        for (langBuf in activeBuffers) {
            val dup = langBuf.buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            dup.position(langBuf.headerSize)
            val langWeight = if (langBuf.isPrimary) 1.0 else 0.95

            val bigrams = findBigramsInBinary(dup, cleanPrev, langWeight, stripAccentsFn)
            results.addAll(bigrams)
        }

        return results
            .groupBy { it.first.lowercase() }
            .mapValues { entry -> entry.value.maxOf { it.second } }
            .entries
            .map { Pair(it.key, it.value) }
            .sortedByDescending { it.second }
            .take(limit)
    }

    private fun findBigramsInBinary(
        buffer: ByteBuffer,
        targetWord: String,
        langWeight: Double,
        stripAccentsFn: (String) -> String
    ): List<Pair<String, Double>> {
        val results = mutableListOf<Pair<String, Double>>()
        val count = try { readPtNodeCount(buffer) } catch (_: Exception) { 0 }

        repeat(count) {
            if (!buffer.hasRemaining()) return@repeat
            val flags = buffer.get().toInt() and 0xFF
            val addrType = flags and 0xC0
            val hasMultipleChars = (flags and 0x20) != 0
            val isTerminal = (flags and 0x10) != 0
            val hasShortcuts = (flags and 0x08) != 0
            val hasBigrams = (flags and 0x04) != 0

            val sb = StringBuilder()
            if (hasMultipleChars) {
                while (true) {
                    val c = readChar(buffer) ?: break
                    sb.appendCodePoint(c)
                }
            } else {
                readChar(buffer)?.let { sb.appendCodePoint(it) }
            }
            val word = sb.toString()

            var freq = -1
            if (isTerminal) freq = if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else 0

            val addressBase = buffer.position()
            val childrenAddr: Int? = when (addrType) {
                0x00 -> null
                0x40 -> if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else null
                0x80 -> if (buffer.remaining() >= 2) buffer.short.toInt() and 0xFFFF else null
                else -> {
                    if (buffer.remaining() >= 3) {
                        val b1 = buffer.get().toInt() and 0xFF
                        val b2 = buffer.get().toInt() and 0xFF
                        val b3 = buffer.get().toInt() and 0xFF
                        (b1 shl 16) or (b2 shl 8) or b3
                    } else null
                }
            }

            if (isTerminal && hasShortcuts && buffer.remaining() >= 2) {
                val size = buffer.short.toInt() and 0xFFFF
                val targetPos = buffer.position() + size - 2
                if (targetPos in 0..buffer.limit()) buffer.position(targetPos)
            }

            val isMatch = isTerminal && (word.lowercase() == targetWord || stripAccentsFn(word.lowercase()) == stripAccentsFn(targetWord))

            if (isMatch && hasBigrams) {
                val bigramEntries = mutableListOf<Pair<Int, Int>>() // Pair(targetAddr, freq)
                val bigramStartPos = buffer.position()

                while (buffer.hasRemaining()) {
                    val bFlags = buffer.get().toInt() and 0xFF
                    val bAddrType = bFlags and 0x30
                    val bFreq = (bFlags and 0x0F) * 16 + 10

                    var offset = 0
                    when (bAddrType) {
                        0x10 -> if (buffer.hasRemaining()) offset = buffer.get().toInt() and 0xFF
                        0x20 -> if (buffer.remaining() >= 2) offset = buffer.short.toInt() and 0xFFFF
                        0x30 -> {
                            if (buffer.remaining() >= 3) {
                                val b1 = buffer.get().toInt() and 0xFF
                                val b2 = buffer.get().toInt() and 0xFF
                                val b3 = buffer.get().toInt() and 0xFF
                                offset = (b1 shl 16) or (b2 shl 8) or b3
                            }
                        }
                    }

                    if (offset > 0) {
                        val targetAddr = bigramStartPos + offset
                        bigramEntries.add(Pair(targetAddr, bFreq))
                    }

                    if ((bFlags and 0x80) == 0) break
                }

                for ((targetAddr, bFreq) in bigramEntries) {
                    if (targetAddr in 0 until buffer.limit()) {
                        val dup = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
                        dup.position(buffer.position())
                        val rootBuf = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
                        val targetWordResolved = resolveWordAtAddress(rootBuf, targetAddr)
                        if (targetWordResolved != null) {
                            val score = (bFreq / 255.0) * langWeight
                            results.add(Pair(targetWordResolved, score))
                        }
                    }
                }
            } else if (isTerminal && hasBigrams) {
                while (buffer.hasRemaining()) {
                    val bFlags = buffer.get().toInt() and 0xFF
                    when (bFlags and 0x30) {
                        0x10 -> if (buffer.hasRemaining()) buffer.get()
                        0x20 -> if (buffer.remaining() >= 2) buffer.short
                        0x30 -> {
                            if (buffer.remaining() >= 3) {
                                buffer.get()
                                buffer.short
                            }
                        }
                    }
                    if ((bFlags and 0x80) == 0) break
                }
            }

            if (childrenAddr != null && targetWord.startsWith(word.lowercase())) {
                val savedPos = buffer.position()
                buffer.position(addressBase + childrenAddr)
                results.addAll(findBigramsInBinary(buffer, targetWord, langWeight, stripAccentsFn))
                buffer.position(savedPos)
            }
        }
        return results
    }

    private fun resolveWordAtAddress(buffer: ByteBuffer, targetAddr: Int, prefix: String = ""): String? {
        val count = try { readPtNodeCount(buffer) } catch (_: Exception) { 0 }
        repeat(count) {
            if (!buffer.hasRemaining()) return null
            val node = parseNextNode(buffer, prefix)
            if (node.addressBase == targetAddr || (node.childrenAddr != null && (node.addressBase + node.childrenAddr) == targetAddr)) {
                return node.word
            }
            if (node.childrenAddr != null) {
                val savedPos = buffer.position()
                buffer.position(node.addressBase + node.childrenAddr)
                val found = resolveWordAtAddress(buffer, targetAddr, node.word)
                if (found != null) return found
                buffer.position(savedPos)
            }
        }
        return null
    }
}
