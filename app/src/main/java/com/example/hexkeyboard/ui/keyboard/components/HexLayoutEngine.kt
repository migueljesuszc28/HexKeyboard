package com.example.hexkeyboard.ui.keyboard.components

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.*

class HexLayoutEngine {

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
        var keyScale: Float = 0.92f
    ) {
        val path: Path = Path()
        val strokePath: Path = Path()
        var currentScale = 1.0f
        var isPressed = false

        init {
            updatePath()
        }

        fun updatePath() {
            val s32 = 0.8660254f
            path.reset()
            strokePath.reset()
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
            val dx = x - cx
            val dy = abs(y - cy)
            val s32 = 0.8660254f
            val r = rx * s32
            val rFull = if (keyScale > 0) (rx / keyScale) * s32 else r

            val limitRight = if (gapRight) r else rFull
            val limitLeft = if (gapLeft) -r else -rFull

            if (dx > limitRight || dx < limitLeft || dy > ry) return false

            val activeR = if (dx >= 0) (if (gapRight) r else rFull) else (if (gapLeft) r else rFull)
            return dy * activeR + abs(dx) * (ry / 2f) <= ry * activeR
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }

    var keyScale: Float = 0.92f
    var heightFactor: Float = 1.0f
    var language: String = "es"
    var layoutType: String = "default"
    var longPressAlternatives: Map<Char, List<String>> = emptyMap()
    var symbolPages: Map<String, List<String>> = emptyMap()
    var fontPages: List<List<String>> = emptyList()

    fun getHexR(vw: Float): Float = if (vw <= 0) 0f else (vw / 8.0f) / sqrt(3.0f).toFloat()

    fun getNumericR(vw: Float, scale: Float = 0.85f): Float {
        if (vw <= 0) return 0f
        return (vw / (5.6f / scale)) / sqrt(3.0f).toFloat()
    }

    fun buildLayout(vw: Float, layoutMode: LayoutMode, symbolPage: String, fontPage: Int, shifted: Boolean, capsLock: Boolean): List<Key> {
        val keys = mutableListOf<Key>()
        when (layoutMode) {
            LayoutMode.ALPHA -> buildHexLayout(vw, keys)
            LayoutMode.NUMERIC -> buildHexNumericLayout(vw, keys)
            LayoutMode.PURE_NUMERIC -> buildHexPureNumericLayout(vw, keys)
            LayoutMode.FONTS -> buildHexFontsLayout(vw, keys, fontPage, shifted, capsLock)
            LayoutMode.SYMBOLS -> buildHexSymbolLayout(vw, keys, symbolPage)
        }
        return keys
    }

    private fun buildHexLayout(vw: Float, keys: MutableList<Key>) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f

        fun cx8(c: Float) = u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = u/2f + (c - 3f) * hw
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

        if (language == "es") {
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx8(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        } else {
            keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx8(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
            keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
            keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
            keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        }

        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(7f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun buildHexPureNumericLayout(vw: Float, keys: MutableList<Key>) {
        val f = heightFactor; val u = vw; val r = getNumericR(vw)
        val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f

        fun cx5(c: Float) = u/2f + (c - 2f) * hw
        fun cx6(c: Float) = u/2f + (c - 2.5f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        listOf("1", "2", "3", "4", "5").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx5(i.toFloat()), cy = ry(0), rx = dr, ry = dry, keyScale = keyScale) }
        listOf("6", "7", "8", "9").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx6(i.toFloat() + 1f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale) }
        keys += Key("⌫", KeyType.DELETE, "DELETE", emptyList(), cx = cx6(5f), cy = ry(1), rx = dr, ry = dry, keyScale = keyScale)
        listOf("0", ",", ".", "-", "+").forEachIndexed { i, l -> keys += Key(l, KeyType.CHAR, l, emptyList(), cx = cx5(i.toFloat()), cy = ry(2), rx = dr, ry = dry, keyScale = keyScale) }
        keys += Key("ABC", KeyType.TOGGLE, "?123", listOf("#+=", "𝔉"), cx = cx6(0f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(1f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(3), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(3), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(3), rx = dr, ry = dry, keyScale = keyScale)
    }

    private fun buildHexNumericLayout(vw: Float, keys: MutableList<Key>) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f

        fun cx8(c: Float) = u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = u/2f + (c - 3f) * hw
        fun cx6(c: Float) = u/2f + (c - 2.5f) * hw
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
        keys += Key(",", KeyType.CHAR, ",", longPressAlternatives[','] ?: emptyList(), cx = cx6(0f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(5f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx6(6f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun buildHexFontsLayout(vw: Float, keys: MutableList<Key>, fontPage: Int, shifted: Boolean, capsLock: Boolean) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f

        fun cx8(c: Float) = u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = u/2f + (c - 3f) * hw
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
        keys += Key((fontPage + 1).toString(), KeyType.FONT_PAGE, "FONT_PAGE", (1..10).map { it.toString() }, cx = cx8(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx8(2f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx8(3f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx8(5f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
        keys += Key("", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(6f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("↩", KeyType.ENTER, "ENTER", emptyList(), cx = cx8(7f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
    }

    private fun buildHexSymbolLayout(vw: Float, keys: MutableList<Key>, symbolPage: String) {
        val f = heightFactor; val u = vw; val r = getHexR(vw); val hw = r * sqrt(3f); val rs = r * 1.5f * f; val tp = r * 0.05f * f
        val dr = r * keyScale; val dry = dr * f

        fun cx8(c: Float) = u/2f + (c - 3.5f) * hw
        fun cx7(c: Float) = u/2f + (c - 3f) * hw
        fun cx6(c: Float) = u/2f + (c - 2.5f) * hw
        fun ry(row: Int) = tp + dry + row * rs

        val symbols = symbolPages[symbolPage] ?: emptyList()
        val pages = symbolPages.keys.toList()
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
        keys += Key("→", KeyType.SYMBOL_PAGE, "→", pages.mapIndexed { i, _ -> (i + 1).toString() }, cx = cx6(0f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key("😀", KeyType.EMOJI, "EMOJI", emptyList(), cx = cx6(1f), cy = ry(4), rx = dr, ry = dry, keyScale = keyScale)
        keys += Key(" ", KeyType.SPACE, " ", emptyList(), cx = cx6(2f), cy = ry(4), rx = dr, ry = dry, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(3f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, gapRight = false, keyScale = keyScale)
        keys += Key("", KeyType.SPACE, " ", emptyList(), cx = cx6(4f), cy = ry(4), rx = dr, ry = dry, gapLeft = false, keyScale = keyScale)
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
        val sampleRx = keys.firstOrNull()?.rx ?: 50f
        val maxThresholdSq = (sampleRx * 2.8f) * (sampleRx * 2.8f)

        for (key in keys) {
            val dx = x - key.cx
            val dy = y - key.cy
            val distSq = dx * dx + dy * dy
            if (distSq < minDistanceSq && distSq <= maxThresholdSq) {
                minDistanceSq = distSq
                closestKey = key
            }
        }
        return closestKey
    }
}
