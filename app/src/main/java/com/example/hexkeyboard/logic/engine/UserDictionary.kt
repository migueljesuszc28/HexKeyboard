package com.example.hexkeyboard.logic.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Gestiona el diccionario del usuario en memoria (Trie), el conteo de aprendizaje
 * y la persistencia atómica en disco sin bloqueos en el hilo principal.
 */
class UserDictionary(private val context: Context) {

    companion object {
        private const val MAX_ENTRIES = 5000
        private const val DEBOUNCE_DELAY_MS = 1000L
        private const val USER_DICT_FILE = "user_dict.txt"
    }

    class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var frequency: Int = 0
        var isWord: Boolean = false
        var isUserWord: Boolean = false
    }

    data class UserWordEntry(val word: String, val timestamp: Long)

    val root = TrieNode()
    private val rwLock = ReentrantReadWriteLock()

    private val userWordsMap = LinkedHashMap<String, UserWordData>()
    private val wordCounts = mutableMapOf<String, Int>()

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var saveJob: Job? = null

    data class UserWordData(
        val frequency: Int,
        val timestamp: Long
    )

    fun loadUserDictionary() {
        rwLock.write {
            root.children.clear()
            userWordsMap.clear()
            wordCounts.clear()

            val file = File(context.filesDir, USER_DICT_FILE)
            if (file.exists()) {
                val baseTime = System.currentTimeMillis() - 1000000L
                file.readLines().forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    val parts = unescapeLine(line)
                    if (parts.isNotEmpty()) {
                        val word = parts[0].trim().lowercase()
                        if (word.isNotEmpty()) {
                            val freq = parts.getOrNull(1)?.toIntOrNull() ?: 200
                            val timestamp = parts.getOrNull(2)?.toLongOrNull() ?: (baseTime + index * 1000L)
                            val count = parts.getOrNull(3)?.toIntOrNull() ?: 0

                            insertLocked(word, freq, isUser = true)
                            userWordsMap[word] = UserWordData(freq, timestamp)
                            if (count > 0) {
                                wordCounts[word] = count
                            }
                        }
                    }
                }
            }
        }
    }

    fun saveUserDictionaryDebounced() {
        saveJob?.cancel()
        saveJob = ioScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            saveUserDictionaryNow()
        }
    }

    fun saveUserDictionaryNow() {
        val entriesSnapshot = rwLock.read {
            userWordsMap.entries.map { (word, data) ->
                val count = wordCounts[word] ?: 0
                Quadruple(word, data.frequency, data.timestamp, count)
            }
        }

        try {
            val targetFile = File(context.filesDir, USER_DICT_FILE)
            val tempFile = File(context.filesDir, "$USER_DICT_FILE.tmp")

            tempFile.bufferedWriter().use { writer ->
                entriesSnapshot.forEach { (word, freq, timestamp, count) ->
                    writer.write(escapeLine(word, freq, timestamp, count))
                    writer.newLine()
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }
        } catch (e: Exception) {
            Log.e("UserDictionary", "Error guardando diccionario de usuario: ${e.message}")
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun addUserWord(word: String, frequency: Int = 800) {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return

        rwLock.write {
            evictIfOverCapacity()
            insertLocked(w, frequency, isUser = true)
            userWordsMap[w] = UserWordData(frequency, System.currentTimeMillis())
        }
        saveUserDictionaryNow()
    }

    fun recordWordCount(word: String): Int {
        return rwLock.write {
            val count = (wordCounts[word] ?: 0) + 1
            wordCounts[word] = count
            count
        }
    }

    fun getWordCount(word: String): Int {
        return rwLock.read { wordCounts[word] ?: 0 }
    }

    fun removeUserWord(word: String) {
        rwLock.write {
            val lower = word.lowercase().trim()
            userWordsMap.remove(lower)
            wordCounts.remove(lower)
            rebuildTrieLocked()
        }
        saveUserDictionaryDebounced()
    }

    fun removeAllUserWords() {
        rwLock.write {
            userWordsMap.clear()
            wordCounts.clear()
            root.children.clear()
        }
        saveUserDictionaryDebounced()
    }

    fun getUserWordEntries(): List<UserWordEntry> {
        return rwLock.read {
            userWordsMap.map { (word, data) -> UserWordEntry(word, data.timestamp) }
        }
    }

    fun getUserWords(): List<String> {
        return rwLock.read { userWordsMap.keys.toList() }
    }

    fun contains(word: String): Boolean {
        return rwLock.read { userWordsMap.containsKey(word.lowercase()) }
    }

    fun isWordInTrie(word: String): Boolean {
        return rwLock.read {
            var current: TrieNode? = root
            for (char in word.lowercase()) {
                current = current?.children?.get(char) ?: return@read false
            }
            current?.isWord == true
        }
    }

    fun <R> readLock(block: (root: TrieNode) -> R): R {
        return rwLock.read { block(root) }
    }

    private fun insertLocked(word: String, freq: Int, isUser: Boolean) {
        var current = root
        word.lowercase().forEach { char ->
            current = current.children.getOrPut(char) { TrieNode() }
        }
        current.isWord = true
        current.isUserWord = isUser
        current.frequency = freq
    }

    private fun rebuildTrieLocked() {
        root.children.clear()
        userWordsMap.forEach { (word, data) ->
            insertLocked(word, data.frequency, isUser = true)
        }
    }

    private fun evictIfOverCapacity() {
        if (userWordsMap.size >= MAX_ENTRIES) {
            val oldest = userWordsMap.keys.firstOrNull()
            if (oldest != null) {
                userWordsMap.remove(oldest)
                wordCounts.remove(oldest)
            }
        }
    }

    private fun escapeLine(word: String, freq: Int, timestamp: Long, count: Int): String {
        val safeWord = word.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n")
        return "$safeWord|$freq|$timestamp|$count"
    }

    private fun unescapeLine(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false

        for (ch in line) {
            if (escaped) {
                when (ch) {
                    'n' -> current.append('\n')
                    else -> current.append(ch)
                }
                escaped = false
            } else when (ch) {
                '\\' -> escaped = true
                '|' -> {
                    parts.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        parts.add(current.toString())
        return parts
    }
}
