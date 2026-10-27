package com.example.hexkeyboard.service

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.hexkeyboard.logic.engine.PredictionEngine
import com.example.hexkeyboard.logic.engine.TouchPoint
import com.example.hexkeyboard.logic.engine.WordSanitizer
import com.example.hexkeyboard.logic.managers.FeedbackManager
import com.example.hexkeyboard.ui.keyboard.components.KeyboardLayoutEngine
import com.example.hexkeyboard.ui.keyboard.components.KeyboardView
import java.text.BreakIterator
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Encapsula y desacopla la lógica de manipulación de texto, autocorrección
 * y borrado seguro para KeyboardService.
 */
class TextEditingEngine(
    private val context: Context,
    private val predictionEngine: PredictionEngine
) {

    data class LastCorrection(val original: String, val corrected: String)

    var lastAutoCorrection: LastCorrection? = null
    var lastUndoneCorrection: String? = null
    var lastSwipedWord: String? = null
    var lastKeyTime: Long = 0
    var lastKeyWasSpace: Boolean = false

    fun calculateLastGraphemeLength(text: String): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val last = it.last()
        val previous = it.previous()
        if (previous == BreakIterator.DONE) return 0
        return last - previous
    }

    fun calculateGraphemeLengthInChars(text: String): Int {
        if (text.isEmpty()) return 0
        var length = 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            length += (end - start)
            start = end
            end = it.next()
        }
        return length
    }

    fun getEffectiveImeAction(info: EditorInfo?): Int {
        if (info == null) return EditorInfo.IME_ACTION_NONE

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION

        if (action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_SEND) {
            return action
        }

        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE
        }

        val isTextClass = (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val isMultiLine = isTextClass && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (isMultiLine) {
            return EditorInfo.IME_ACTION_NONE
        }

        if (action == EditorInfo.IME_ACTION_DONE && isTextClass) {
            return EditorInfo.IME_ACTION_NONE
        }

        return action
    }

    fun handleChar(
        text: String,
        ic: InputConnection,
        keyboardView: KeyboardView?,
        autoCorrect: Boolean,
        autoCorrectForField: Boolean,
        doubleSpacePeriod: Boolean,
        allowPersonalizedLearning: Boolean,
        currentWordTouchPoints: CopyOnWriteArrayList<TouchPoint>,
        symbolsTypedCount: Int,
        onSymbolsTypedCountChanged: (Int) -> Unit,
        onStateUpdated: () -> Unit
    ) {
        keyboardView?.let { view ->
            if (view.layoutMode == KeyboardLayoutEngine.LayoutMode.NUMERIC || view.layoutMode == KeyboardLayoutEngine.LayoutMode.SYMBOLS) {
                if (text == " ") {
                    if (symbolsTypedCount > 0) {
                        view.layoutMode = KeyboardLayoutEngine.LayoutMode.ALPHA
                        onSymbolsTypedCountChanged(0)
                    }
                } else {
                    onSymbolsTypedCountChanged(symbolsTypedCount + 1)
                }
            } else {
                onSymbolsTypedCountChanged(0)
            }
        }

        if (text != " ") {
            lastAutoCorrection = null
            lastUndoneCorrection = null
        }

        ic.beginBatchEdit()

        if (text in listOf(".", ",", "!", "?", ";", ":")) {
            val textBefore = ic.getTextBeforeCursor(2, 0)
            if (textBefore != null && textBefore.endsWith(" ")) {
                ic.deleteSurroundingText(1, 0)
            }
            lastSwipedWord = null
        } else if (text != " ") {
            lastSwipedWord = null
        }

        if (text == " " && lastKeyWasSpace && (System.currentTimeMillis() - lastKeyTime < 500)) {
            if (doubleSpacePeriod) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastKeyWasSpace = false
                lastAutoCorrection = null
                ic.endBatchEdit()
                onStateUpdated()
                return
            }
        }

        val isPunctuation = text in listOf(".", ",", "!", "?", ";", ":")
        val isSeparator = text == " " || isPunctuation || text == "\n"
        var performedAutocorrect = false

        if (isSeparator && autoCorrect && autoCorrectForField && !lastKeyWasSpace) {
            val textBefore = ic.getTextBeforeCursor(50, 0) ?: ""
            val rawTokens = textBefore.toString().split(" ", "\n", "\t").filter { it.isNotEmpty() }
            val rawOriginalToken = rawTokens.lastOrNull() ?: ""
            val prev1Raw = rawTokens.getOrNull(rawTokens.size - 2)
            val prev2Raw = rawTokens.getOrNull(rawTokens.size - 3)

            val prev1Clean = WordSanitizer.sanitizeToken(prev1Raw ?: "").cleanWord
            val prev2Clean = WordSanitizer.sanitizeToken(prev2Raw ?: "").cleanWord

            if (rawOriginalToken.isNotEmpty()) {
                if (rawOriginalToken == lastUndoneCorrection) {
                    lastUndoneCorrection = null
                } else {
                    val correction = predictionEngine.getAutocorrection(
                        typedWord = rawOriginalToken,
                        previousWord = prev1Clean,
                        prev2 = prev2Clean,
                        touchPoints = ArrayList(currentWordTouchPoints)
                    )

                    if (correction != null && correction.text.lowercase() != rawOriginalToken.lowercase()) {
                        val replacement = if (text == " ") correction.text + " " else correction.text
                        replaceLastWord(replacement, ic)
                        lastAutoCorrection = LastCorrection(rawOriginalToken, correction.text)
                        lastUndoneCorrection = null
                        if (allowPersonalizedLearning) {
                            predictionEngine.learnFromInput(correction.text, prev1Clean, prev2Clean)
                        }
                        predictionEngine.registerConfirmedWordTouch(ArrayList(currentWordTouchPoints), correction.text)
                        currentWordTouchPoints.clear()
                        performedAutocorrect = true

                        if (text == " ") {
                            ic.endBatchEdit()
                            lastKeyTime = System.currentTimeMillis()
                            lastKeyWasSpace = true
                            onStateUpdated()
                            return
                        }
                    }
                }
            }
        }

        if (isSeparator) {
            currentWordTouchPoints.clear()
        }

        if (isSeparator && !performedAutocorrect && !lastKeyWasSpace) {
            val before = ic.getTextBeforeCursor(60, 0) ?: ""
            val rawTokens = before.split(" ", "\n", "\t").filter { it.isNotEmpty() }
            if (rawTokens.isNotEmpty()) {
                val currentRaw = rawTokens.last()
                val prev1Raw = rawTokens.getOrNull(rawTokens.size - 2)
                val prev2Raw = rawTokens.getOrNull(rawTokens.size - 3)

                val currentClean = WordSanitizer.sanitizeToken(currentRaw).cleanWord
                val prev1Clean = WordSanitizer.sanitizeToken(prev1Raw ?: "").cleanWord
                val prev2Clean = WordSanitizer.sanitizeToken(prev2Raw ?: "").cleanWord

                val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                if (emailRegex.matches(currentRaw)) {
                    predictionEngine.addUserWord(currentRaw)
                } else if (currentClean.isNotEmpty() && allowPersonalizedLearning) {
                    predictionEngine.learnFromInput(currentClean, prev1Clean, prev2Clean)
                }
            }
        }

        if (text == " ") {
            lastAutoCorrection = null
            lastUndoneCorrection = null
        }

        ic.commitText(text, 1)
        ic.endBatchEdit()

        lastKeyTime = System.currentTimeMillis()
        lastKeyWasSpace = (text == " ")

        onStateUpdated()
    }

    fun handleDelete(
        ic: InputConnection,
        keyboardView: KeyboardView?,
        undoCorrectionOnBackspace: Boolean,
        currentWordTouchPoints: CopyOnWriteArrayList<TouchPoint>,
        symbolsTypedCount: Int,
        onSymbolsTypedCountChanged: (Int) -> Unit,
        onStateUpdated: () -> Unit
    ) {
        if (lastSwipedWord != null) {
            val sw = lastSwipedWord!!
            val textBefore = ic.getTextBeforeCursor(sw.length + 1, 0)
            if (textBefore != null && textBefore.toString() == "$sw ") {
                keyboardView?.triggerVibration(FeedbackManager.HapticType.DELETE)
                keyboardView?.triggerSound()
                ic.beginBatchEdit()
                ic.deleteSurroundingText(sw.length + 1, 0)
                ic.endBatchEdit()
                lastSwipedWord = null
                onStateUpdated()
                return
            }
            lastSwipedWord = null
        }

        if (undoCorrectionOnBackspace) {
            lastAutoCorrection?.let { correction ->
                val textBefore = ic.getTextBeforeCursor(correction.corrected.length + 1, 0)
                if (textBefore != null && textBefore.toString() == "${correction.corrected} ") {
                    keyboardView?.triggerVibration(FeedbackManager.HapticType.DELETE)
                    keyboardView?.triggerSound()
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(correction.corrected.length + 1, 0)
                    ic.commitText(correction.original, 1)
                    ic.endBatchEdit()
                    lastUndoneCorrection = correction.original
                    lastAutoCorrection = null
                    onStateUpdated()
                    return
                }
            }
        }
        lastAutoCorrection = null
        lastUndoneCorrection = null

        val selectedText = ic.getSelectedText(0)
        val hasSelection = !selectedText.isNullOrEmpty()
        val textBefore = ic.getTextBeforeCursor(40, 0)
        val hasTextBefore = !textBefore.isNullOrEmpty()

        if (hasSelection || hasTextBefore) {
            keyboardView?.triggerVibration(FeedbackManager.HapticType.DELETE)
            keyboardView?.triggerSound()
            if (currentWordTouchPoints.isNotEmpty()) currentWordTouchPoints.removeAt(currentWordTouchPoints.size - 1)
            if (symbolsTypedCount > 0) onSymbolsTypedCountChanged(symbolsTypedCount - 1)
        } else {
            return
        }

        if (hasSelection) {
            ic.commitText("", 1)
        } else {
            val textStr = textBefore.toString()
            val len = calculateLastGraphemeLength(textStr)

            if (len > 1) {
                ic.deleteSurroundingText(len, 0)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }

        onStateUpdated()
    }

    fun replaceLastWord(newWord: String, ic: InputConnection) {
        ic.beginBatchEdit()
        val textBefore = ic.getTextBeforeCursor(30, 0)
        var trailingPunctuation = ""
        if (!textBefore.isNullOrEmpty() && !textBefore.endsWith(" ")) {
            val lastToken = textBefore.toString().split(" ", "\n").last()
            val sanitized = WordSanitizer.sanitizeToken(lastToken)
            trailingPunctuation = sanitized.trailingPunctuation
            val deleteLen = calculateGraphemeLengthInChars(lastToken)
            ic.deleteSurroundingText(deleteLen, 0)
        }

        val replacementText = if (trailingPunctuation.isNotEmpty()) {
            newWord.trimEnd() + trailingPunctuation + " "
        } else {
            newWord
        }

        ic.commitText(replacementText, 1)
        ic.endBatchEdit()
    }

    fun handleEnter(
        ic: InputConnection,
        editorInfo: EditorInfo?,
        allowPersonalizedLearning: Boolean,
        onStateUpdated: () -> Unit
    ) {
        ic.beginBatchEdit()

        val before = ic.getTextBeforeCursor(40, 0) ?: ""
        val words = before.split(" ", "\n", "\t").filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            val currentWord = words.last()
            val previousWord = if (words.size > 1) words[words.size - 2] else null
            val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
            if (emailRegex.matches(currentWord)) {
                predictionEngine.addUserWord(currentWord)
            } else if (allowPersonalizedLearning) {
                predictionEngine.learnFromInput(currentWord, previousWord)
            }
        }

        val effectiveAction = getEffectiveImeAction(editorInfo)

        if (effectiveAction != EditorInfo.IME_ACTION_NONE && effectiveAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(effectiveAction)
        } else {
            ic.commitText("\n", 1)
        }

        ic.endBatchEdit()

        onStateUpdated()
    }
}
