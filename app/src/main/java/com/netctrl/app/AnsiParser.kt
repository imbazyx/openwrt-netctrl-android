package com.netctrl.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Минимальный ANSI-парсер для терминального вывода.
 * Поддерживает SGR (Select Graphic Rendition) коды для цветов и стилей.
 */
object AnsiParser {

    private val sgrRegex = "\u001b\\[([0-9;]*)m".toRegex()

    fun parse(raw: String): AnnotatedString = buildAnnotatedString {
        var pos = 0
        var fg = Color(0xFFE0E0E0)
        var bold = false
        var underline = false

        fun flush(end: Int) {
            if (pos >= end) return
            val chunk = raw.substring(pos, end)
            withStyle(
                SpanStyle(
                    color = fg,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null
                )
            ) {
                append(chunk)
            }
        }

        sgrRegex.findAll(raw).forEach { match ->
            flush(match.range.first)
            pos = match.range.last + 1
            val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
            
            for (code in codes) {
                when (code) {
                    0 -> { fg = Color(0xFFE0E0E0); bold = false; underline = false }
                    1 -> bold = true
                    4 -> underline = true
                    22 -> bold = false
                    24 -> underline = false
                    // Standard foreground colors
                    30 -> fg = Color(0xFF000000)
                    31 -> fg = Color(0xFFCD3131)
                    32 -> fg = Color(0xFF0DBC79)
                    33 -> fg = Color(0xFFE5E510)
                    34 -> fg = Color(0xFF2472C8)
                    35 -> fg = Color(0xFFBC3FBC)
                    36 -> fg = Color(0xFF11A8CD)
                    37 -> fg = Color(0xFFE5E5E5)
                    // Bright foreground colors
                    90 -> fg = Color(0xFF666666)
                    91 -> fg = Color(0xFFF14C4C)
                    92 -> fg = Color(0xFF23D18B)
                    93 -> fg = Color(0xFFF5F543)
                    94 -> fg = Color(0xFF3B8EEA)
                    95 -> fg = Color(0xFFD670D6)
                    96 -> fg = Color(0xFF29B8DB)
                    97 -> fg = Color(0xFFFFFFFF)
                }
            }
        }
        flush(raw.length)
    }
}
