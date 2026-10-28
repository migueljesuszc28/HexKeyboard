package com.example.mantiskeyboard.ui.keyboard.components

import android.graphics.Path
import kotlin.math.*

class KeyboardLayoutEngine {

    enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, TOGGLE, SYMBOL_PAGE, EMOJI, CLIPBOARD, FUNCTIONS, FONT_PAGE, LANGUAGE }
    enum class LayoutMode { ALPHA, NUMERIC, SYMBOLS, PURE_NUMERIC, FONTS }

    data class Key(
        val display: String,
        val type: KeyType,
        val value: String = "",
        val alternatives: List<String> = emptyList(),
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
        val gapLeft: Boolean = true,
        val gapRight: Boolean = true,
        val isHalfLeft: Boolean = false,
        val isHalfRight: Boolean = false,
        var keyScale: Float = 0.92f,
        val isRectangular: Boolean = false
    ) {
        val path: Path = Path()
        val strokePath: Path = Path()
        var currentScale = 1.0f
        var isPressed = false

        init {
            updatePath()
        }

        private companion object {
            val tempRectF = android.graphics.RectF()
        }

        fun updatePath() {
            path.reset()
            strokePath.reset()
            if (isRectangular) {
                val rxCorner = when (type) {
                    KeyType.SHIFT, KeyType.DELETE -> min(rx, ry) * 0.90f
                    else -> min(rx, ry) * 0.55f
                }

                tempRectF.set(cx - rx, cy - ry, cx + rx, cy + ry)
                path.addRoundRect(tempRectF, rxCorner, rxCorner, Path.Direction.CW)
                strokePath.addRoundRect(tempRectF, rxCorner, rxCorner, Path.Direction.CW)
                return
            }
            val s32 = 0.8660254f
            val h = ry
            val r = rx * s32
            val rFull = if (keyScale > 0) (rx / keyScale) * s32 else r

            val rounding = 0.28f
            val overlap = 0.4f // Solapamiento para evitar líneas de anti-aliasing
            fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

            if (isHalfLeft) {
                val margin = rFull - r
                val edgeX = cx + margin
                val p0x = cx; val p0y = cy - h
                val p1x = if (gapRight) cx + r else cx + rFull + overlap; val p1y = cy - h / 2f
                val p2x = if (gapRight) cx + r else cx + rFull + overlap; val p2y = cy + h / 2f
                val p3x = cx; val p3y = cy + h

                val startX = edgeX; val startY = cy - h + (h * rounding)
                path.moveTo(startX, startY)
                strokePath.moveTo(startX, startY)

                path.quadTo(edgeX, cy - h, lerp(edgeX, p1x, rounding), lerp(cy - h, p1y, rounding))
                strokePath.quadTo(edgeX, cy - h, lerp(edgeX, p1x, rounding), lerp(cy - h, p1y, rounding))

                path.lineTo(lerp(p1x, p0x, rounding), lerp(p1y, p0y, rounding))
                strokePath.lineTo(lerp(p1x, p0x, rounding), lerp(p1y, p0y, rounding))

                if (gapRight) {
                    path.quadTo(p1x, p1y, lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
                    strokePath.quadTo(p1x, p1y, lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
                } else {
                    path.lineTo(p1x, p1y); path.lineTo(lerp(p1x, p2x, rounding), lerp(p1y, p2y, rounding))
                    strokePath.lineTo(p1x, p1y); strokePath.moveTo(p2x, p2y)
                }

                path.lineTo(lerp(p2x, p1x, rounding), lerp(p2y, p1y, rounding))
                if (gapRight) strokePath.lineTo(lerp(p2x, p1x, rounding), lerp(p2y, p1y, rounding))

                if (gapRight) {
                    path.quadTo(p2x, p2y, lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
                    strokePath.quadTo(p2x, p2y, lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
                } else {
                    path.lineTo(p2x, p2y); path.lineTo(lerp(p2x, p3x, rounding), lerp(p2y, p3y, rounding))
                    strokePath.lineTo(p2x, p2y)
                }

                path.lineTo(lerp(p3x, p2x, rounding), lerp(p3y, p2y, rounding))
                strokePath.lineTo(lerp(p3x, p2x, rounding), lerp(p3y, p2y, rounding))

                path.quadTo(edgeX, cy + h, edgeX, cy + h - (h * rounding))
                strokePath.quadTo(edgeX, cy + h, edgeX, cy + h - (h * rounding))

                path.lineTo(edgeX, cy - h + (h * rounding))
                strokePath.lineTo(edgeX, cy - h + (h * rounding))

            } else if (isHalfRight) {
                val margin = rFull - r
                val edgeX = cx - margin
                val p3x = cx; val p3y = cy + h
                val p4x = if (gapLeft) cx - r else cx - rFull - overlap; val p4y = cy + h / 2f
                val p5x = if (gapLeft) cx - r else cx - rFull - overlap; val p5y = cy - h / 2f
                val p0x = cx; val p0y = cy - h

                val startX = edgeX; val startY = cy + h - (h * rounding)
                path.moveTo(startX, startY)
                strokePath.moveTo(startX, startY)

                path.quadTo(edgeX, cy + h, lerp(edgeX, p4x, rounding), lerp(cy + h, p4y, rounding))
                strokePath.quadTo(edgeX, cy + h, lerp(edgeX, p4x, rounding), lerp(cy + h, p4y, rounding))

                path.lineTo(lerp(p4x, p3x, rounding), lerp(p4y, p3y, rounding))
                strokePath.lineTo(lerp(p4x, p3x, rounding), lerp(p4y, p3y, rounding))

                if (gapLeft) {
                    path.quadTo(p4x, p4y, lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
                    strokePath.quadTo(p4x, p4y, lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
                } else {
                    path.lineTo(p4x, p4y); path.lineTo(lerp(p4x, p5x, rounding), lerp(p4y, p5y, rounding))
                    strokePath.lineTo(p4x, p4y); strokePath.moveTo(p5x, p5y)
                }

                path.lineTo(lerp(p5x, p4x, rounding), lerp(p5y, p4y, rounding))
                if (gapLeft) strokePath.lineTo(lerp(p5x, p4x, rounding), lerp(p5y, p4y, rounding))

                if (gapLeft) {
                    path.quadTo(p5x, p5y, lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
                    strokePath.quadTo(p5x, p5y, lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
                } else {
                    path.lineTo(p5x, p5y); path.lineTo(lerp(p5x, p0x, rounding), lerp(p5y, p0y, rounding))
                    strokePath.lineTo(p5x, p5y)
                }

                path.lineTo(lerp(p0x, p5x, rounding), lerp(p0y, p5y, rounding))
                strokePath.lineTo(lerp(p0x, p5x, rounding), lerp(p0y, p5y, rounding))

                path.quadTo(edgeX, cy - h, edgeX, cy - h + (h * rounding))
                strokePath.quadTo(edgeX, cy - h, edgeX, cy - h + (h * rounding))

                path.lineTo(edgeX, cy + h - (h * rounding))
                strokePath.lineTo(edgeX, cy + h - (h * rounding))

            } else {
                val px = FloatArray(6)
                val py = FloatArray(6)
                px[0] = cx; py[0] = cy - h
                px[1] = if (gapRight) cx + r else cx + rFull + overlap; py[1] = cy - h / 2f
                px[2] = if (gapRight) cx + r else cx + rFull + overlap; py[2] = cy + h / 2f
                px[3] = cx; py[3] = cy + h
                px[4] = if (gapLeft) cx - r else cx - rFull - overlap; py[4] = cy + h / 2f
                px[5] = if (gapLeft) cx - r else cx - rFull - overlap; py[5] = cy - h / 2f

                val startX = lerp(px[0], px[5], rounding)
                val startY = lerp(py[0], py[5], rounding)
                path.moveTo(startX, startY)
                strokePath.moveTo(startX, startY)

                for (i in 0..5) {
                    val next = (i + 1) % 6
                    val useRound = when (i) {
                        1, 2 -> gapRight
                        4, 5 -> gapLeft
                        else -> true
                    }

                    if (useRound) {
                        path.quadTo(px[i], py[i], lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))
                        strokePath.quadTo(px[i], py[i], lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))
                    } else {
                        path.lineTo(px[i], py[i])
                        path.lineTo(lerp(px[i], px[next], rounding), lerp(py[i], py[next], rounding))
                        strokePath.lineTo(px[i], py[i])
                    }

                    val isSharedEdge = when (i) {
                        1 -> !gapRight
                        4 -> !gapLeft
                        else -> false
                    }

                    if (isSharedEdge) {
                        path.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
                        strokePath.moveTo(px[next], py[next])
                    } else {
                        if (i < 5) {
                            path.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
                            strokePath.lineTo(lerp(px[next], px[i], rounding), lerp(py[next], py[i], rounding))
                        } else {
                            path.lineTo(startX, startY)
                            strokePath.lineTo(startX, startY)
                        }
                    }
                }
            }
            path.close()
        }

        fun contains(x: Float, y: Float): Boolean {
            if (isRectangular) {
                val fullRx = if (keyScale > 0f) rx / keyScale else rx
                val fullRy = if (keyScale > 0f) ry / keyScale else ry
                return abs(x - cx) <= fullRx && abs(y - cy) <= fullRy
            }
            val dx = x - cx
            val dy = abs(y - cy)
            val s32 = 0.8660254f
            val r = rx * s32
            val rFull = if (keyScale > 0) (rx / keyScale) * s32 else r

            val limitRight = if (gapRight) r else rFull
            val limitLeft = if (gapLeft) -r else -rFull
            val fullRy = if (keyScale > 0) ry / keyScale else ry

            if (dx > limitRight || dx < limitLeft || dy > fullRy) return false

            val activeR = if (dx >= 0) (if (gapRight) r else rFull) else (if (gapLeft) r else rFull)
            return dy * activeR + abs(dx) * (fullRy / 2f) <= fullRy * activeR
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }

    var keyScale: Float = 0.92f
    var heightFactor: Float = 1.0f
    var language: String = "es"
    var layoutType: String = "default"
    var isSplitMode: Boolean = false
    var splitGapRatio: Float = 0.18f
    var longPressAlternatives: Map<Char, List<String>> = emptyMap()
    var symbolPages: Map<String, List<String>> = emptyMap()
    var fontPages: List<List<String>> = emptyList()

    fun getHexR(vw: Float): Float {
        if (vw <= 0) return 0f
        val gapWidth = if (isSplitMode) vw * splitGapRatio else 0f
        val availableWidth = vw - gapWidth
        return (availableWidth / 8.0f) / sqrt(3.0f)
    }

    fun getNumericR(vw: Float, scale: Float = 0.85f): Float {
        if (vw <= 0) return 0f
        val gapWidth = if (isSplitMode) vw * splitGapRatio else 0f
        val availableWidth = vw - gapWidth
        return (availableWidth / (5.6f / scale)) / sqrt(3.0f)
    }

    private fun getColumnX(c: Float, centerCol: Float, u: Float, hw: Float, gapWidth: Float): Float {
        if (gapWidth <= 0f) {
            return u / 2f + (c - centerCol) * hw
        }
        return if (c < centerCol) {
            (c + (3.5f - centerCol) + 0.5f) * hw
        } else if (c > centerCol) {
            u - ((7.0f - c) + (centerCol - 3.5f) + 0.5f) * hw
        } else {
            (c + (3.5f - centerCol) + 0.5f) * hw
        }
    }

    fun buildLayout(vw: Float, layoutMode: LayoutMode, symbolPage: String, fontPage: Int, shifted: Boolean, capsLock: Boolean): List<Key> {
        val keys = mutableListOf<Key>()
        when (layoutMode) {
            LayoutMode.ALPHA -> {
                if (layoutType == "qwerty") {
                    buildStandardQwertyLayout(vw, keys)
                } else {
                    buildHexLayout(vw, keys)
                }
            }
            LayoutMode.NUMERIC -> {
                if (layoutType == "qwerty") {
                    buildStandardQwertyNumericLayout(vw, keys)
                } else {
                    buildHexNumericLayout(vw, keys)
                }
            }
            LayoutMode.PURE_NUMERIC -> {
                if (layoutType == "qwerty") {
                    buildStandardQwertyPureNumericLayout(vw, keys)
                } else {
                    buildHexPureNumericLayout(vw, keys)
                }
            }
            LayoutMode.FONTS -> {
                if (layoutType == "qwerty") {
                    buildStandardQwertyFontsLayout(vw, keys, fontPage, shifted, capsLock)
                } else {
                    buildHexFontsLayout(vw, keys, fontPage, shifted, capsLock)
                }
            }
            LayoutMode.SYMBOLS -> {
                if (layoutType == "qwerty") {
                    buildStandardQwertySymbolsLayout(vw, keys, symbolPage)
                } else {
                    buildHexSymbolLayout(vw, keys, symbolPage)
                }
            }
        }
        return keys
    }

    private fun buildStandardQwertyLayout(vw: Float, keys: MutableList<Key>) {
        val u = vw
        val f = heightFactor
        val rHex = getHexR(u)
        val drHex = rHex * keyScale
        val dryHex = drHex * f
        val tpHex = rHex * 0.05f * f
        val totalH = tpHex + 2f * dryHex + 4f * rHex * 1.5f * f + rHex * 0.15f * f

        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f
        val availW = u - gapWidth
        val keyW = availW / 10.0f
        val dr = (keyW / 2.0f) * 0.95f * keyScale

       // val qwertyRowHeight = (totalH / 4.0f) * 0.85f
        val qwertyRowHeight = totalH / 4.0f
        val dry = (qwertyRowHeight / 2.0f) * 0.86f * keyScale

        fun ry(row: Int) = (row + 0.5f) * qwertyRowHeight

        fun getRectX(c: Float): Float {
            if (gapWidth <= 0f) {
                return (c + 0.5f) * keyW
            }
            return if (c < 5.0f) {
                (c + 0.5f) * keyW
            } else {
                u - ((10.0f - c - 1.0f) + 0.5f) * keyW
            }
        }

        // --- ROW 0: Q W E R T Y U I O P ---
        val row0 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        row0.forEachIndexed { i, l ->
            keys += Key(
                display = l,
                type = KeyType.CHAR,
                value = l,
                alternatives = longPressAlternatives[l[0]] ?: emptyList(),
                cx = getRectX(i.toFloat()),
                cy = ry(0),
                rx = dr,
                ry = dry,
                keyScale = keyScale,
                isRectangular = true
            )
        }

        // --- ROW 1: A S D F G H J K L (Ñ in ES) ---
        val isEs = language == "es"
        val row1 = if (isEs) {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ")
        } else {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        }

        if (isEs) {
            row1.forEachIndexed { i, l ->
                keys += Key(
                    display = l,
                    type = KeyType.CHAR,
                    value = l,
                    alternatives = longPressAlternatives[l[0]] ?: emptyList(),
                    cx = getRectX(i.toFloat()),
                    cy = ry(1),
                    rx = dr,
                    ry = dry,
                    keyScale = keyScale,
                    isRectangular = true
                )
            }
        } else {
            row1.forEachIndexed { i, l ->
                val colX = if (gapWidth <= 0f) {
                    keyW * 0.5f + (i + 0.5f) * keyW
                } else {
                    if (i < 5) (i + 0.5f) * keyW + keyW * 0.25f
                    else u - ((9f - i - 1f) + 0.5f) * keyW - keyW * 0.25f
                }
                keys += Key(
                    display = l,
                    type = KeyType.CHAR,
                    value = l,
                    alternatives = longPressAlternatives[l[0]] ?: emptyList(),
                    cx = colX,
                    cy = ry(1),
                    rx = dr,
                    ry = dry,
                    keyScale = keyScale,
                    isRectangular = true
                )
            }
        }

        // --- ROW 2: SHIFT + Z X C V B N M + DELETE ---
        val shiftW = keyW * 1.50f
        val drShift = (shiftW / 2.0f) * 0.95f * keyScale
        val shiftCxLeft = shiftW / 2.0f
        val deleteCxRight = u - shiftW / 2.0f

        keys += Key(
            display = "⇧",
            type = KeyType.SHIFT,
            value = "SHIFT",
            alternatives = emptyList(),
            cx = shiftCxLeft,
            cy = ry(2),
            rx = drShift,
            ry = dry,
            keyScale = keyScale,
            isRectangular = true
        )

        val row2Chars = listOf("z", "x", "c", "v", "b", "n", "m")
        val middleAvailW = availW - (shiftW * 2.0f)
        val middleKeyW = middleAvailW / 7.0f
        val drMiddle = (middleKeyW / 2.0f) * 0.95f * keyScale

        row2Chars.forEachIndexed { i, l ->
            val colX = if (gapWidth <= 0f) {
                shiftW + (i + 0.5f) * middleKeyW
            } else {
                if (i < 4) shiftW + (i + 0.5f) * middleKeyW
                else u - shiftW - ((7f - i - 1f) + 0.5f) * middleKeyW
            }
            keys += Key(
                display = l,
                type = KeyType.CHAR,
                value = l,
                alternatives = longPressAlternatives[l[0]] ?: emptyList(),
                cx = colX,
                cy = ry(2),
                rx = drMiddle,
                ry = dry,
                keyScale = keyScale,
                isRectangular = true
            )
        }

        keys += Key(
            display = "⌫",
            type = KeyType.DELETE,
            value = "DELETE",
            alternatives = emptyList(),
            cx = deleteCxRight,
            cy = ry(2),
            rx = drShift,
            ry = dry,
            keyScale = keyScale,
            isRectangular = true
        )

        // --- ROW 3: ?123 + , + 😀 + Space + . + ↩ ---
        val marginX = (keyW * (1.0f - keyScale)) / 2.0f

        val toggleW = keyW * 1.50f
        val drToggle = (toggleW / 2.0f) * 0.95f * keyScale

        val commaW = keyW * 1.0f
        val drComma = (commaW / 2.0f) * 0.95f * keyScale

        val emojiW = keyW * 1.0f
        val drEmoji = (emojiW / 2.0f) * 0.95f * keyScale

        val periodW = keyW * 1.0f
        val drPeriod = (periodW / 2.0f) * 0.95f * keyScale

        val enterW = keyW * 1.50f
        val drEnter = (enterW / 2.0f) * 0.95f * keyScale

        if (isSplitMode) {
            val leftSpaceW = keyW * 1.8f
            val drSpaceL = leftSpaceW / 2.0f - marginX

            val xToggle = toggleW / 2.0f
            val xComma = toggleW + commaW / 2.0f
            val xEmoji = toggleW + commaW + emojiW / 2.0f
            val xSpaceL = toggleW + commaW + emojiW + leftSpaceW / 2.0f

            keys += Key("?123", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = xToggle, cy = ry(3), rx = drToggle, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = xComma, cy = ry(3), rx = drComma, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceL, cy = ry(3), rx = drSpaceL, ry = dry, keyScale = keyScale, isRectangular = true)

            val rightSpaceW = keyW * 1.8f
            val drSpaceR = rightSpaceW / 2.0f - marginX

            val xEnter = u - enterW / 2.0f
            val xPeriod = u - enterW - periodW / 2.0f
            val xSpaceR = u - enterW - periodW - rightSpaceW / 2.0f

            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceR, cy = ry(3), rx = drSpaceR, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        } else {
            val spaceW = u - (toggleW + commaW + emojiW + periodW + enterW)
            val drSpace = spaceW / 2.0f - marginX

            val xToggle = toggleW / 2.0f
            val xComma = toggleW + commaW / 2.0f
            val xEmoji = toggleW + commaW + emojiW / 2.0f
            val xSpace = toggleW + commaW + emojiW + spaceW / 2.0f
            val xPeriod = toggleW + commaW + emojiW + spaceW + periodW / 2.0f
            val xEnter = u - enterW / 2.0f

            keys += Key("?123", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = xToggle, cy = ry(3), rx = drToggle, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = xComma, cy = ry(3), rx = drComma, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpace, cy = ry(3), rx = drSpace, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        }
    }

    private fun buildStandardQwertyNumericLayout(vw: Float, keys: MutableList<Key>) {
        val u = vw
        val f = heightFactor
        val rHex = getHexR(u)
        val drHex = rHex * keyScale
        val dryHex = drHex * f
        val tpHex = rHex * 0.05f * f
        val totalH = tpHex + 2f * dryHex + 4f * rHex * 1.5f * f + rHex * 0.15f * f

        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f
        val availW = u - gapWidth
        val keyW = availW / 10.0f
        val dr = (keyW / 2.0f) * 0.95f * keyScale

        val qwertyRowHeight = totalH / 4.0f
        val dry = (qwertyRowHeight / 2.0f) * 0.86f * keyScale

        fun ry(row: Int) = (row + 0.5f) * qwertyRowHeight

        fun getRectX(c: Float): Float {
            if (gapWidth <= 0f) {
                return (c + 0.5f) * keyW
            }
            return if (c < 5.0f) {
                (c + 0.5f) * keyW
            } else {
                u - ((10.0f - c - 1.0f) + 0.5f) * keyW
            }
        }

        // --- ROW 0: 1 2 3 4 5 6 7 8 9 0 ---
        val row0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        row0.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = getRectX(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        // --- ROW 1: @ # $ _ & - + ( ) / ---
        val row1 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
        row1.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, if (l.length == 1) longPressAlternatives[l[0]] ?: emptyList() else emptyList(), cx = getRectX(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        // --- ROW 2: 8 symbol keys + ⌫ (Delete) ---
        val deleteW = keyW * 1.50f
        val drDelete = (deleteW / 2.0f) * 0.95f * keyScale
        val xDeleteRight = u - deleteW / 2.0f

        val symAvailW = u - deleteW
        val symW = symAvailW / 8.0f
        val drSym = (symW / 2.0f) * 0.95f * keyScale

        val row2Syms = listOf("%", "*", "\"", "'", ":", ";", "!", "?")
        row2Syms.forEachIndexed { i, l ->
            val colX = (i + 0.5f) * symW
            keys += Key(l, KeyType.CHAR, l, if (l.length == 1) longPressAlternatives[l[0]] ?: emptyList() else emptyList(), cx = colX, cy = ry(2), rx = drSym, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = xDeleteRight, cy = ry(2), rx = drDelete, ry = dry, keyScale = keyScale, isRectangular = true)

        // --- ROW 3: ABC + , + 😀 + Space + . + ↩ ---
        val marginX = (keyW * (1.0f - keyScale)) / 2.0f

        val abcW = keyW * 1.50f
        val drAbc = (abcW / 2.0f) * 0.95f * keyScale
        val commaW = keyW * 1.0f
        val drComma = (commaW / 2.0f) * 0.95f * keyScale
        val emojiW = keyW * 1.0f
        val drEmoji = (emojiW / 2.0f) * 0.95f * keyScale
        val periodW = keyW * 1.0f
        val drPeriod = (periodW / 2.0f) * 0.95f * keyScale
        val enterW = keyW * 1.50f
        val drEnter = (enterW / 2.0f) * 0.95f * keyScale

        if (isSplitMode) {
            val leftSpaceW = keyW * 1.8f
            val drSpaceL = leftSpaceW / 2.0f - marginX

            val xAbc = abcW / 2.0f
            val xComma = abcW + commaW / 2.0f
            val xEmoji = abcW + commaW + emojiW / 2.0f
            val xSpaceL = abcW + commaW + emojiW + leftSpaceW / 2.0f

            keys += Key("ABC", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = xAbc, cy = ry(3), rx = drAbc, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = xComma, cy = ry(3), rx = drComma, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceL, cy = ry(3), rx = drSpaceL, ry = dry, keyScale = keyScale, isRectangular = true)

            val rightSpaceW = keyW * 1.8f
            val drSpaceR = rightSpaceW / 2.0f - marginX

            val xEnter = u - enterW / 2.0f
            val xPeriod = u - enterW - periodW / 2.0f
            val xSpaceR = u - enterW - periodW - rightSpaceW / 2.0f

            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceR, cy = ry(3), rx = drSpaceR, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        } else {
            val spaceW = u - (abcW + commaW + emojiW + periodW + enterW)
            val drSpace = spaceW / 2.0f - marginX

            val xAbc = abcW / 2.0f
            val xComma = abcW + commaW / 2.0f
            val xEmoji = abcW + commaW + emojiW / 2.0f
            val xSpace = abcW + commaW + emojiW + spaceW / 2.0f
            val xPeriod = abcW + commaW + emojiW + spaceW + periodW / 2.0f
            val xEnter = u - enterW / 2.0f

            keys += Key("ABC", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = xAbc, cy = ry(3), rx = drAbc, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = xComma, cy = ry(3), rx = drComma, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpace, cy = ry(3), rx = drSpace, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        }
    }

    private fun buildStandardQwertySymbolsLayout(vw: Float, keys: MutableList<Key>, symbolPage: String) {
        val u = vw
        val f = heightFactor
        val rHex = getHexR(u)
        val drHex = rHex * keyScale
        val dryHex = drHex * f
        val tpHex = rHex * 0.05f * f
        val totalH = tpHex + 2f * dryHex + 4f * rHex * 1.5f * f + rHex * 0.15f * f

        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f
        val availW = u - gapWidth
        val keyW = availW / 10.0f
        val dr = (keyW / 2.0f) * 0.95f * keyScale

        val qwertyRowHeight = totalH / 4.0f
        val dry = (qwertyRowHeight / 2.0f) * 0.86f * keyScale

        fun ry(row: Int) = (row + 0.5f) * qwertyRowHeight

        fun getRectX(c: Float): Float {
            if (gapWidth <= 0f) {
                return (c + 0.5f) * keyW
            }
            return if (c < 5.0f) {
                (c + 0.5f) * keyW
            } else {
                u - ((10.0f - c - 1.0f) + 0.5f) * keyW
            }
        }

        val symbols = symbolPages[symbolPage] ?: emptyList()
        val pages = symbolPages.keys.toList()
        val currentPageIndex = pages.indexOf(symbolPage).coerceAtLeast(0)
        val displaySymbolPageStr = (currentPageIndex + 1).toString()
        val symbolPageAlternatives = (1..pages.size).map { it.toString() }
        var idx = 0
        fun nextSym(): String = if (idx < symbols.size) symbols[idx++] else ""

        for (i in 0 until 10) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = getRectX(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        for (i in 0 until 10) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = getRectX(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        // --- ROW 2: 8 symbol keys + ⌫ (Delete) ---
        val deleteW = keyW * 1.50f
        val drDelete = (deleteW / 2.0f) * 0.95f * keyScale
        val xDeleteRight = u - deleteW / 2.0f

        val symAvailW = u - deleteW
        val symW = symAvailW / 8.0f
        val drSym = (symW / 2.0f) * 0.95f * keyScale

        for (i in 0 until 8) {
            val s = nextSym()
            val colX = (i + 0.5f) * symW
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = colX, cy = ry(2), rx = drSym, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = xDeleteRight, cy = ry(2), rx = drDelete, ry = dry, keyScale = keyScale, isRectangular = true)

        // --- ROW 3: ABC + SymbolPage + 😀 + Space + . + ↩ ---
        val marginX = (keyW * (1.0f - keyScale)) / 2.0f

        val abcW = keyW * 1.50f
        val drAbc = (abcW / 2.0f) * 0.95f * keyScale
        val symPageW = keyW * 1.0f
        val drSymPage = (symPageW / 2.0f) * 0.95f * keyScale
        val emojiW = keyW * 1.0f
        val drEmoji = (emojiW / 2.0f) * 0.95f * keyScale
        val periodW = keyW * 1.0f
        val drPeriod = (periodW / 2.0f) * 0.95f * keyScale
        val enterW = keyW * 1.50f
        val drEnter = (enterW / 2.0f) * 0.95f * keyScale

        if (isSplitMode) {
            val leftSpaceW = keyW * 1.8f
            val drSpaceL = leftSpaceW / 2.0f - marginX

            val xAbc = abcW / 2.0f
            val xSymPage = abcW + symPageW / 2.0f
            val xEmoji = abcW + symPageW + emojiW / 2.0f
            val xSpaceL = abcW + symPageW + emojiW + leftSpaceW / 2.0f

            keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "𝔉"), cx = xAbc, cy = ry(3), rx = drAbc, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(displaySymbolPageStr, KeyType.SYMBOL_PAGE, "SYMBOL_PAGE", symbolPageAlternatives, cx = xSymPage, cy = ry(3), rx = drSymPage, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceL, cy = ry(3), rx = drSpaceL, ry = dry, keyScale = keyScale, isRectangular = true)

            val rightSpaceW = keyW * 1.8f
            val drSpaceR = rightSpaceW / 2.0f - marginX

            val xEnter = u - enterW / 2.0f
            val xPeriod = u - enterW - periodW / 2.0f
            val xSpaceR = u - enterW - periodW - rightSpaceW / 2.0f

            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceR, cy = ry(3), rx = drSpaceR, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        } else {
            val spaceW = u - (abcW + symPageW + emojiW + periodW + enterW)
            val drSpace = spaceW / 2.0f - marginX

            val xAbc = abcW / 2.0f
            val xSymPage = abcW + symPageW / 2.0f
            val xEmoji = abcW + symPageW + emojiW / 2.0f
            val xSpace = abcW + symPageW + emojiW + spaceW / 2.0f
            val xPeriod = abcW + symPageW + emojiW + spaceW + periodW / 2.0f
            val xEnter = u - enterW / 2.0f

            keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "𝔉"), cx = xAbc, cy = ry(3), rx = drAbc, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(displaySymbolPageStr, KeyType.SYMBOL_PAGE, "SYMBOL_PAGE", symbolPageAlternatives, cx = xSymPage, cy = ry(3), rx = drSymPage, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpace, cy = ry(3), rx = drSpace, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        }
    }

    private fun buildStandardQwertyPureNumericLayout(vw: Float, keys: MutableList<Key>) {
        val u = vw
        val f = heightFactor
        val rHex = getHexR(u)
        val drHex = rHex * keyScale
        val dryHex = drHex * f
        val tpHex = rHex * 0.05f * f
        val totalH = tpHex + 2f * dryHex + 4f * rHex * 1.5f * f + rHex * 0.15f * f

        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f
        val availW = u - gapWidth
        val colW = availW / 5.0f
        val dr = (colW / 2.0f) * keyScale
        val drHalf = (colW / 4.0f) * keyScale

        val qwertyRowHeight = totalH / 4.0f
        val dry = (qwertyRowHeight / 2.0f) * 0.94f * keyScale

        fun ry(row: Int) = (row + 0.5f) * qwertyRowHeight

        fun getColX(c: Float): Float {
            if (gapWidth <= 0f) {
                return (c + 0.5f) * colW
            }
            return if (c < 2.5f) {
                (c + 0.5f) * colW
            } else {
                u - ((5.0f - c - 1.0f) + 0.5f) * colW
            }
        }

        // --- Column 0: + (row 0), - (row 1), * / (row 2), ABC (row 3) ---
        keys += Key("+", KeyType.CHAR, "+", emptyList(), cx = getColX(0f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("-", KeyType.CHAR, "-", emptyList(), cx = getColX(0f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("*", KeyType.CHAR, "*", emptyList(), cx = getColX(-0.25f), cy = ry(2), rx = drHalf, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("/", KeyType.CHAR, "/", emptyList(), cx = getColX(0.25f), cy = ry(2), rx = drHalf, ry = dry, keyScale = keyScale, isRectangular = true)

        // --- Row 0: 1 2 3 % ---
        keys += Key("1", KeyType.CHAR, "1", longPressAlternatives['1'] ?: emptyList(), cx = getColX(1f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("2", KeyType.CHAR, "2", longPressAlternatives['2'] ?: emptyList(), cx = getColX(2f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("3", KeyType.CHAR, "3", longPressAlternatives['3'] ?: emptyList(), cx = getColX(3f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("%", KeyType.CHAR, "%", emptyList(), cx = getColX(4f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)

        // --- Row 1: 4 5 6 [Space] ---
        keys += Key("4", KeyType.CHAR, "4", longPressAlternatives['4'] ?: emptyList(), cx = getColX(1f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("5", KeyType.CHAR, "5", longPressAlternatives['5'] ?: emptyList(), cx = getColX(2f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("6", KeyType.CHAR, "6", longPressAlternatives['6'] ?: emptyList(), cx = getColX(3f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = getColX(4f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)

        // --- Row 2: 7 8 9 ⌫ ---
        keys += Key("7", KeyType.CHAR, "7", longPressAlternatives['7'] ?: emptyList(), cx = getColX(1f), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("8", KeyType.CHAR, "8", longPressAlternatives['8'] ?: emptyList(), cx = getColX(2f), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("9", KeyType.CHAR, "9", longPressAlternatives['9'] ?: emptyList(), cx = getColX(3f), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = getColX(4f), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)

        // --- Row 3: ABC , # 0 = . ↩ ---
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("#+=", "𝔉"), cx = getColX(0f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key(",", KeyType.CHAR, ",", emptyList(), cx = getColX(0.75f), cy = ry(3), rx = drHalf, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("#", KeyType.CHAR, "#", emptyList(), cx = getColX(1.25f), cy = ry(3), rx = drHalf, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("0", KeyType.CHAR, "0", longPressAlternatives['0'] ?: emptyList(), cx = getColX(2f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("=", KeyType.CHAR, "=", emptyList(), cx = getColX(2.75f), cy = ry(3), rx = drHalf, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = getColX(3.25f), cy = ry(3), rx = drHalf, ry = dry, keyScale = keyScale, isRectangular = true)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = getColX(4f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
    }

    private fun buildStandardQwertyFontsLayout(vw: Float, keys: MutableList<Key>, fontPage: Int, shifted: Boolean, capsLock: Boolean) {
        val u = vw
        val f = heightFactor
        val rHex = getHexR(u)
        val drHex = rHex * keyScale
        val dryHex = drHex * f
        val tpHex = rHex * 0.05f * f
        val totalH = tpHex + 2f * dryHex + 4f * rHex * 1.5f * f + rHex * 0.15f * f

        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f
        val availW = u - gapWidth
        val keyW = availW / 10.0f
        val dr = (keyW / 2.0f) * 0.95f * keyScale

        val qwertyRowHeight = totalH / 4.0f
        val dry = (qwertyRowHeight / 2.0f) * 0.86f * keyScale

        fun ry(row: Int) = (row + 0.5f) * qwertyRowHeight

        fun getRectX(c: Float): Float {
            if (gapWidth <= 0f) {
                return (c + 0.5f) * keyW
            }
            return if (c < 5.0f) {
                (c + 0.5f) * keyW
            } else {
                u - ((10.0f - c - 1.0f) + 0.5f) * keyW
            }
        }

        val currentFonts = if (fontPages.size > fontPage) fontPages[fontPage] else List(52) { "" }
        val offset = if (shifted || capsLock) 0 else 26
        val letterOrder = getFontLayoutLetterOrder()
        var letterIdx = 0
        fun nextChar(): String {
            val letter = letterOrder.getOrElse(letterIdx) { 'a' }
            letterIdx++
            val alphaIndex = letter - 'a'
            return currentFonts.getOrElse(alphaIndex + offset) { letter.toString() }
        }

        for (i in 0 until 10) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = getRectX(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        val isEs = language == "es"
        if (isEs) {
            for (i in 0 until 10) {
                val char = nextChar()
                keys += Key(char, KeyType.CHAR, char, emptyList(), cx = getRectX(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
            }
        } else {
            for (i in 0 until 9) {
                val char = nextChar()
                val colX = if (gapWidth <= 0f) {
                    keyW * 0.5f + (i + 0.5f) * keyW
                } else {
                    if (i < 5) (i + 0.5f) * keyW + keyW * 0.25f
                    else u - ((9f - i - 1f) + 0.5f) * keyW - keyW * 0.25f
                }
                keys += Key(char, KeyType.CHAR, char, emptyList(), cx = colX, cy = ry(1), rx = dr, ry = dry, keyScale = keyScale, isRectangular = true)
            }
        }

        val shiftW = keyW * 1.50f
        val drShift = (shiftW / 2.0f) * 0.95f * keyScale
        val shiftCxLeft = shiftW / 2.0f
        val deleteCxRight = u - shiftW / 2.0f

        keys += Key("⇧", KeyType.SHIFT, "SHIFT", emptyList(), cx = shiftCxLeft, cy = ry(2), rx = drShift, ry = dry, keyScale = keyScale, isRectangular = true)

        val middleAvailW = availW - (shiftW * 2.0f)
        val middleKeyW = middleAvailW / 7.0f
        val drMiddle = (middleKeyW / 2.0f) * 0.95f * keyScale

        for (i in 0 until 7) {
            val char = nextChar()
            val colX = if (gapWidth <= 0f) {
                shiftW + (i + 0.5f) * middleKeyW
            } else {
                if (i < 4) shiftW + (i + 0.5f) * middleKeyW
                else u - shiftW - ((7f - i - 1f) + 0.5f) * middleKeyW
            }
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = colX, cy = ry(2), rx = drMiddle, ry = dry, keyScale = keyScale, isRectangular = true)
        }

        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = deleteCxRight, cy = ry(2), rx = drShift, ry = dry, keyScale = keyScale, isRectangular = true)

        val marginX = (keyW * (1.0f - keyScale)) / 2.0f

        val abcW = keyW * 1.50f
        val drAbc = (abcW / 2.0f) * 0.95f * keyScale
        val fontPageW = keyW * 1.0f
        val drFontPage = (fontPageW / 2.0f) * 0.95f * keyScale
        val emojiW = keyW * 1.0f
        val drEmoji = (emojiW / 2.0f) * 0.95f * keyScale
        val periodW = keyW * 1.0f
        val drPeriod = (periodW / 2.0f) * 0.95f * keyScale
        val enterW = keyW * 1.50f
        val drEnter = (enterW / 2.0f) * 0.95f * keyScale

        val fontDisplayStr = (fontPage + 1).toString()
        val fontPageAlternatives = (1..10).map { it.toString() }

        if (isSplitMode) {
            val leftSpaceW = keyW * 1.8f
            val drSpaceL = leftSpaceW / 2.0f - marginX

            val xAbc = abcW / 2.0f
            val xFontPage = abcW + fontPageW / 2.0f
            val xEmoji = abcW + fontPageW + emojiW / 2.0f
            val xSpaceL = abcW + fontPageW + emojiW + leftSpaceW / 2.0f

            keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "#+=", "𝔉"), cx = xAbc, cy = ry(3), rx = drAbc, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(fontDisplayStr, KeyType.FONT_PAGE, "FONT_PAGE", fontPageAlternatives, cx = xFontPage, cy = ry(3), rx = drFontPage, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceL, cy = ry(3), rx = drSpaceL, ry = dry, keyScale = keyScale, isRectangular = true)

            val rightSpaceW = keyW * 1.8f
            val drSpaceR = rightSpaceW / 2.0f - marginX

            val xEnter = u - enterW / 2.0f
            val xPeriod = u - enterW - periodW / 2.0f
            val xSpaceR = u - enterW - periodW - rightSpaceW / 2.0f

            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpaceR, cy = ry(3), rx = drSpaceR, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        } else {
            val spaceW = u - (abcW + fontPageW + emojiW + periodW + enterW)
            val drSpace = spaceW / 2.0f - marginX

            val xAbc = abcW / 2.0f
            val xFontPage = abcW + fontPageW / 2.0f
            val xEmoji = abcW + fontPageW + emojiW / 2.0f
            val xSpace = abcW + fontPageW + emojiW + spaceW / 2.0f
            val xPeriod = abcW + fontPageW + emojiW + spaceW + periodW / 2.0f
            val xEnter = u - enterW / 2.0f

            keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "#+=", "𝔉"), cx = xAbc, cy = ry(3), rx = drAbc, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(fontDisplayStr, KeyType.FONT_PAGE, "FONT_PAGE", fontPageAlternatives, cx = xFontPage, cy = ry(3), rx = drFontPage, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = xEmoji, cy = ry(3), rx = drEmoji, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = xSpace, cy = ry(3), rx = drSpace, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key(".", KeyType.CHAR, ".", longPressAlternatives['.'] ?: emptyList(), cx = xPeriod, cy = ry(3), rx = drPeriod, ry = dry, keyScale = keyScale, isRectangular = true)
            keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = xEnter, cy = ry(3), rx = drEnter, ry = dry, keyScale = keyScale, isRectangular = true)
        }
    }

    private fun buildHexLayout(vw: Float, keys: MutableList<Key>) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f

        fun cx8(c: Float) = getColumnX(c, 3.5f, u, hw, gapWidth)
        fun cx7(c: Float) = getColumnX(c, 3.0f, u, hw, gapWidth)
        fun ry(row: Int) = tp + dry + row * rs

        val row0 = if (layoutType == "qwerty") listOf("q","w","e","r","t","y") else listOf("q","w","f","g","p","b")
        val row1 = if (layoutType == "qwerty") listOf("u","i","o","p","a","s","d") else listOf("k","o","u","d","i","c","m")
        val row2 = if (layoutType == "qwerty") listOf("f","g","h","j","k","l","z","x") else listOf("a","e","s","r","h","t","n","l")
        val row3Chars = if (layoutType == "qwerty") listOf("c","v","b","n","m") else listOf("v","y","x","z","j")

        row0.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx8(i.toFloat() + 1f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale)
        }

        row1.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale)
        }

        row2.forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale)
        }

        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false, keyScale = keyScale)

        val row3Full = listOf("⇧") + row3Chars + listOf("⌫")
        row3Full.forEachIndexed { i, l ->
            val type = when(l) {
                "⇧" -> KeyType.SHIFT
                "⌫" -> KeyType.DELETE
                else -> KeyType.CHAR
            }
            val value = when(l) {
                "⇧" -> "SHIFT"
                "⌫" -> "DELETE"
                else -> l
            }
            val alt = if (type == KeyType.CHAR) (longPressAlternatives[l[0]] ?: emptyList()) else emptyList()
            val finalCx = when(type) {
                KeyType.SHIFT -> cx7(0.0f)
                KeyType.DELETE -> cx7(6.0f)
                else -> cx7(i.toFloat())
            }
            val gL = type != KeyType.SHIFT
            val gR = type != KeyType.DELETE
            keys += Key(l, type, value, alt, cx = finalCx, cy = ry(3), rx = dr, ry = dry, gapLeft = gL, gapRight = gR, keyScale = keyScale)
        }

        keys += Key("?123", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = cx8(0f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)

        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)

        if (isSplitMode) {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = true, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = true, keyScale = keyScale)
        } else {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        if (language != "es") {
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(7f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun buildHexPureNumericLayout(vw: Float, keys: MutableList<Key>) {
        val f = heightFactor; val u = vw; val r = getNumericR(vw)
        val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f

        fun cx5(c: Float) = getColumnX(c, 2.0f, u, hw, gapWidth)
        fun cx6(c: Float) = getColumnX(c, 2.5f, u, hw, gapWidth)
        fun ry(row: Int) = tp + dry + row * rs

        listOf("1", "2", "3", "4", "5").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx5(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale) }
        listOf("6", "7", "8", "9").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx6(i.toFloat() + 1f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale) }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx6(5f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale)
        listOf("0", ",", ".", "-", "+").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx5(i.toFloat()), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale) }
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("#+=", "𝔉"), cx = cx6(0f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(1f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)

        if (isSplitMode) {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(3), rx = dr, ry = dry, gapLeft = true, gapRight = true, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(3), rx = dr, ry = dry, gapLeft = true, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, gapRight = true, keyScale = keyScale)
        } else {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(3), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
    }

    private fun buildHexNumericLayout(vw: Float, keys: MutableList<Key>) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f

        fun cx8(c: Float) = getColumnX(c, 3.5f, u, hw, gapWidth)
        fun cx7(c: Float) = getColumnX(c, 3.0f, u, hw, gapWidth)
        fun cx6(c: Float) = getColumnX(c, 2.5f, u, hw, gapWidth)
        fun ry(row: Int) = tp + dry + row * rs

        listOf("1","2","3","4","5","6").forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, (longPressAlternatives[l[0]] ?: emptyList()), cx = cx6(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale)
        }
        listOf("7","8","9","0","-","+","=").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, longPressAlternatives[l[0]] ?: emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale) }
        listOf("@", "#", "$", "_", "&", "(", ")", "*").forEachIndexed { i, l ->
            keys += Key(l, KeyType.CHAR, l, if (l.length == 1) (longPressAlternatives[l[0]] ?: emptyList()) else emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale)
        }
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false, keyScale = keyScale)
        listOf("⇧", "\"", "'", ":", ".", "?", "⌫").forEachIndexed { i, l ->
            val type = when(l) {
                "⇧" -> KeyType.SHIFT
                "⌫" -> KeyType.DELETE
                else -> KeyType.CHAR
            }
            val value = when(l) {
                "⇧" -> "SHIFT"
                "⌫" -> "DELETE"
                else -> l
            }
            val alt = if (type == KeyType.CHAR) (longPressAlternatives[l[0]] ?: emptyList()) else emptyList()
            val finalCx = when(type) {
                KeyType.SHIFT -> cx7(0.0f)
                KeyType.DELETE -> cx7(6.0f)
                else -> cx7(i.toFloat())
            }
            val gL = type != KeyType.SHIFT
            val gR = type != KeyType.DELETE
            keys += Key(l, type, value, alt, cx = finalCx, cy = ry(3), rx = dr, ry = dry, gapLeft = gL, gapRight = gR, keyScale = keyScale)
        }
        keys += Key("ABC", KeyType.TOGGLE, "123", listOf("#+=", "𝔉"), cx = cx6(-1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(0f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx6(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)

        if (isSplitMode) {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = true, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = true, keyScale = keyScale)
        } else {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun buildHexFontsLayout(vw: Float, keys: MutableList<Key>, fontPage: Int, shifted: Boolean, capsLock: Boolean) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f

        fun cx8(c: Float) = getColumnX(c, 3.5f, u, hw, gapWidth)
        fun cx7(c: Float) = getColumnX(c, 3.0f, u, hw, gapWidth)
        fun ry(row: Int) = tp + dry + row * rs

        val currentFonts = if (fontPages.size > fontPage) fontPages[fontPage] else List(52) { "" }
        val offset = if (shifted || capsLock) 0 else 26
        val letterOrder = getFontLayoutLetterOrder()
        var letterIdx = 0
        fun nextChar(): String {
            val letter = letterOrder.getOrElse(letterIdx) { 'a' }
            letterIdx++
            val alphaIndex = letter - 'a'
            return currentFonts.getOrElse(alphaIndex + offset) { letter.toString() }
        }

        for (i in 0 until 6) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx8(i.toFloat() + 1f), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale)
        }
        for (i in 0 until 7) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale)
        }
        for (i in 0 until 8) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale)
        }
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false, keyScale = keyScale)
        keys += Key("⇧", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(0.0f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        for (i in 0 until 5) {
            val char = nextChar()
            keys += Key(char, KeyType.CHAR, char, emptyList(), cx = cx7(i.toFloat() + 1f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
        }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(6.0f), cy = ry(3), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "#+=", "𝔉"), cx = cx8(0f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key((fontPage + 1).toString(), KeyType.FONT_PAGE, "FONT_PAGE", (1..10).map { it.toString() }, cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)

        if (isSplitMode) {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = true, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = true, keyScale = keyScale)
        } else {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(7f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun buildHexSymbolLayout(vw: Float, keys: MutableList<Key>, symbolPage: String) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f
        val gapWidth = if (isSplitMode) u * splitGapRatio else 0f

        fun cx8(c: Float) = getColumnX(c, 3.5f, u, hw, gapWidth)
        fun cx7(c: Float) = getColumnX(c, 3.0f, u, hw, gapWidth)
        fun cx6(c: Float) = getColumnX(c, 2.5f, u, hw, gapWidth)
        fun ry(row: Int) = tp + dry + row * rs

        val symbols = symbolPages[symbolPage] ?: emptyList()
        val pages = symbolPages.keys.toList()
        val currentPageIndex = pages.indexOf(symbolPage).coerceAtLeast(0)
        val displaySymbolPageStr = (currentPageIndex + 1).toString()
        val symbolPageAlternatives = (1..pages.size).map { it.toString() }
        var idx = 0
        fun nextSym(): String = if (idx < symbols.size) symbols[idx++] else ""

        for (i in 0 until 6) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx6(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale)
        }
        for (i in 0 until 7) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx7(i.toFloat()), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale)
        }
        for (i in 0 until 8) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx8(i.toFloat()), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale)
        }
        keys += Key("", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(-1.0f), cy = ry(3), rx = dr, ry = dry, isHalfLeft = true, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(7.0f), cy = ry(3), rx = dr, ry = dry, isHalfRight = true, gapLeft = false, keyScale = keyScale)
        keys += Key("⇧", KeyType.SHIFT, "SHIFT", emptyList(), cx = cx7(0.0f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        for (i in 0 until 5) {
            val s = nextSym()
            keys += Key(s, KeyType.CHAR, s, if (s.length == 1) longPressAlternatives[s[0]] ?: emptyList() else emptyList(), cx = cx7(i.toFloat() + 1f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
        }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx7(6.0f), cy = ry(3), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("123", "𝔉"), cx = cx6(-1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(0f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(displaySymbolPageStr, KeyType.SYMBOL_PAGE, "SYMBOL_PAGE", symbolPageAlternatives, cx = cx6(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)

        if (isSplitMode) {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = true, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = true, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = true, keyScale = keyScale)
        } else {
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun getFontLayoutLetterOrder(): List<Char> {
        return if (layoutType == "qwerty") {
            listOf('q','w','e','r','t','y','u','i','o','p','a','s','d','f','g','h','j','k','l','z','x','c','v','b','n','m')
        } else {
            listOf('q','w','f','g','p','b','k','o','u','d','i','c','m','a','e','s','r','h','t','n','l','v','y','x','z','j')
        }
    }

    fun findKeyAt(x: Float, y: Float, keys: List<Key>): Key? {
        val exactMatch = keys.find { it.contains(x, y) }
        if (exactMatch != null) return exactMatch

        if (keys.isEmpty()) return null

        var closestKey: Key? = null
        var minDistanceSq = Float.MAX_VALUE

        for (key in keys) {
            val dx = x - key.cx
            val dy = y - key.cy
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestKey = key
            }
        }
        return closestKey
    }
}
