package com.pjournal.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Editor-mode Markdown highlight: keeps all markers visible, applies syntax coloring.
 * Returns an AnnotatedString with the same length as the input text.
 */
fun parseMarkdownHighlight(
    text: String,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    accentColor: Color,
    baseFontSize: androidx.compose.ui.unit.TextUnit = 16.sp
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split('\n')
        var inCodeBlock = false

        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) append('\n')

            val trimmed = line.trimStart()

            // Fenced code block toggle
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                withStyle(SpanStyle(color = primaryColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                    append(line)
                }
                continue
            }

            if (inCodeBlock) {
                withStyle(SpanStyle(color = accentColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                    append(line)
                }
                continue
            }

            if (line.isBlank()) {
                append(line)
                continue
            }

            // Detect block-level prefix and highlight it
            val (role, prefixLen) = detectBlockRole(trimmed)
            val blockPrefixStyle = when (role) {
                MdRole.Heading1, MdRole.Heading2, MdRole.Heading3,
                MdRole.Heading4, MdRole.Heading5, MdRole.Heading6 ->
                    SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)
                MdRole.Blockquote -> SpanStyle(color = primaryColor)
                MdRole.ListItem -> SpanStyle(color = primaryColor)
                else -> SpanStyle(color = textColor)
            }
            val headingContentStyle = when (role) {
                MdRole.Heading1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.5f, color = textColor)
                MdRole.Heading2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.3f, color = textColor)
                MdRole.Heading3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.15f, color = textColor)
                MdRole.Heading4 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.05f, color = textColor)
                MdRole.Heading5 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize, color = textColor)
                MdRole.Heading6 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 0.9f, color = mutedColor)
                MdRole.Blockquote -> SpanStyle(fontStyle = FontStyle.Italic, color = mutedColor)
                else -> SpanStyle(color = textColor)
            }

            if (prefixLen > 0) {
                // Leading whitespace before the prefix
                val leadingWs = line.length - trimmed.length
                if (leadingWs > 0) {
                    withStyle(SpanStyle(color = textColor)) { append(line.substring(0, leadingWs)) }
                }
                // Highlight the prefix (# , > , - , etc.)
                withStyle(blockPrefixStyle) {
                    append(trimmed.substring(0, prefixLen))
                }
                // Rest of the line with heading content style + inline highlights
                val rest = trimmed.substring(prefixLen)
                if (rest.isNotEmpty()) {
                    appendInlineWithMarkers(rest, headingContentStyle, primaryColor, accentColor)
                }
            } else {
                // Normal line: apply inline highlighting keeping markers
                appendInlineWithMarkers(line, SpanStyle(color = textColor), primaryColor, accentColor)
            }
        }
    }
}

private data class EditorInlineMatch(
    val start: Int,
    val markerStart: Int,
    val markerEnd: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val endMarkerStart: Int,
    val endMarkerEnd: Int,
    val end: Int,
    val markerStyle: SpanStyle,
    val contentStyle: SpanStyle
)

private fun findEditorInlineMatches(line: String, primaryColor: Color, accentColor: Color): List<EditorInlineMatch> {
    val len = line.length
    val result = mutableListOf<EditorInlineMatch>()
    val used = BooleanArray(len)
    var i = 0

    while (i < len) {
        if (used[i]) { i++; continue }
        val m = editorMatchAt(line, i, primaryColor, accentColor)
        if (m != null && (m.start until m.end).none { used[it] }) {
            result.add(m)
            for (j in m.start until m.end) used[j] = true
            i = m.end
        } else {
            i++
        }
    }
    result.sortBy { it.start }
    return result
}

private fun editorMatchAt(line: String, i: Int, primaryColor: Color, accentColor: Color): EditorInlineMatch? {
    val len = line.length
    val markerStyle = SpanStyle(color = primaryColor)
    val monoStyle = SpanStyle(color = accentColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)

    // **bold**
    if (i + 4 < len && line[i] == '*' && line[i + 1] == '*' && line[i + 2] != '*') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '*' && line[e + 1] == '*' && (e + 2 >= len || line[e + 2] != '*')) {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = i + 2,
                    contentStart = i + 2, contentEnd = e,
                    endMarkerStart = e, endMarkerEnd = e + 2,
                    end = e + 2,
                    markerStyle = markerStyle,
                    contentStyle = SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)
                )
            }
            e++
        }
    }
    // ~~strikethrough~~
    if (i + 4 < len && line[i] == '~' && line[i + 1] == '~') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '~' && line[e + 1] == '~') {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = i + 2,
                    contentStart = i + 2, contentEnd = e,
                    endMarkerStart = e, endMarkerEnd = e + 2,
                    end = e + 2,
                    markerStyle = markerStyle,
                    contentStyle = SpanStyle(textDecoration = TextDecoration.LineThrough, color = accentColor)
                )
            }
            e++
        }
    }
    // ==highlight==
    if (i + 4 < len && line[i] == '=' && line[i + 1] == '=' && line[i + 2] != '=') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '=' && line[e + 1] == '=' && (e + 2 >= len || line[e + 2] != '=')) {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = i + 2,
                    contentStart = i + 2, contentEnd = e,
                    endMarkerStart = e, endMarkerEnd = e + 2,
                    end = e + 2,
                    markerStyle = markerStyle,
                    contentStyle = SpanStyle(color = accentColor)
                )
            }
            e++
        }
    }
    // <u>underline</u>
    if (i + 6 < len && line[i] == '<' && line[i + 1] == 'u' && line[i + 2] == '>') {
        var e = i + 3
        while (e + 3 < len) {
            if (line[e] == '<' && line[e + 1] == '/' && line[e + 2] == 'u' && line[e + 3] == '>') {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = i + 3,
                    contentStart = i + 3, contentEnd = e,
                    endMarkerStart = e, endMarkerEnd = e + 4,
                    end = e + 4,
                    markerStyle = markerStyle,
                    contentStyle = SpanStyle(textDecoration = TextDecoration.Underline, color = accentColor)
                )
            }
            e++
        }
    }
    // `code`
    if (line[i] == '`') {
        var e = i + 1
        while (e < len) {
            if (line[e] == '`') {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = i + 1,
                    contentStart = i + 1, contentEnd = e,
                    endMarkerStart = e, endMarkerEnd = e + 1,
                    end = e + 1,
                    markerStyle = markerStyle,
                    contentStyle = monoStyle
                )
            }
            e++
        }
    }
    // *italic*
    if (i + 2 < len && line[i] == '*' && line[i + 1] != '*' && (i == 0 || line[i - 1] != '*')) {
        var e = i + 1
        while (e < len) {
            if (line[e] == '*' && (e + 1 >= len || line[e + 1] != '*') && line[e - 1] != '*') {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = i + 1,
                    contentStart = i + 1, contentEnd = e,
                    endMarkerStart = e, endMarkerEnd = e + 1,
                    end = e + 1,
                    markerStyle = markerStyle,
                    contentStyle = SpanStyle(fontStyle = FontStyle.Italic, color = accentColor)
                )
            }
            e++
        }
    }
    return null
}

private fun AnnotatedString.Builder.appendInlineWithMarkers(
    line: String,
    baseStyle: SpanStyle,
    primaryColor: Color,
    accentColor: Color
) {
    val matches = findEditorInlineMatches(line, primaryColor, accentColor)
    if (matches.isEmpty()) {
        withStyle(baseStyle) { append(line) }
        return
    }

    var pos = 0
    for (m in matches) {
        // Plain text before match
        if (pos < m.start) {
            withStyle(baseStyle) { append(line.substring(pos, m.start)) }
        }
        // Opening marker
        withStyle(m.markerStyle) { append(line.substring(m.markerStart, m.markerEnd)) }
        // Styled content
        withStyle(m.contentStyle) { append(line.substring(m.contentStart, m.contentEnd)) }
        // Closing marker
        withStyle(m.markerStyle) { append(line.substring(m.endMarkerStart, m.endMarkerEnd)) }
        pos = m.end
    }
    if (pos < line.length) {
        withStyle(baseStyle) { append(line.substring(pos)) }
    }
}

/**
 * Parses markdown text into a styled AnnotatedString for display in the viewer.
 * Headings, blockquotes, lists, code blocks, and inline formatting are supported.
 */
fun parseMarkdown(
    text: String,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    accentYellow: Color,
    highlightBg: Color,
    baseFontSize: androidx.compose.ui.unit.TextUnit = 16.sp
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split('\n')
        var inCodeBlock = false

        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) append('\n')

            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                withStyle(SpanStyle(color = mutedColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                    append(trimmed)
                }
                continue
            }

            if (inCodeBlock) {
                withStyle(SpanStyle(color = accentYellow, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) {
                    append(line)
                }
                continue
            }

            if (line.isBlank()) {
                append(line)
                continue
            }

            val (role, contentStart) = detectBlockRole(trimmed)
            val baseStyle = roleBaseStyle(role, textColor, primaryColor, mutedColor, baseFontSize)

            if (contentStart > 0) {
                // Strip block prefix for clean viewer rendering
                val rest = line.substring(line.length - trimmed.length + contentStart)
                if (rest.isNotBlank()) {
                    appendInlineHighlighted(rest, baseStyle, mutedColor, accentYellow, highlightBg)
                }
            } else {
                appendInlineHighlighted(line, baseStyle, mutedColor, accentYellow, highlightBg)
            }
        }
    }
}

enum class MdRole {
    Heading1, Heading2, Heading3, Heading4, Heading5, Heading6,
    Blockquote, CodeBlock, ListItem, Normal
}

private fun detectBlockRole(trimmed: String): Pair<MdRole, Int> {
    return when {
        trimmed.startsWith("###### ") -> MdRole.Heading6 to 7
        trimmed.startsWith("##### ") -> MdRole.Heading5 to 6
        trimmed.startsWith("#### ") -> MdRole.Heading4 to 5
        trimmed.startsWith("### ") -> MdRole.Heading3 to 4
        trimmed.startsWith("## ") -> MdRole.Heading2 to 3
        trimmed.startsWith("# ") -> MdRole.Heading1 to 2
        trimmed.startsWith("> ") -> MdRole.Blockquote to 2
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> MdRole.ListItem to 2
        else -> MdRole.Normal to 0
    }
}

private fun roleBaseStyle(
    role: MdRole,
    textColor: Color,
    primaryColor: Color,
    mutedColor: Color,
    baseFontSize: androidx.compose.ui.unit.TextUnit
): SpanStyle {
    return when (role) {
        MdRole.Heading1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.5f, color = primaryColor)
        MdRole.Heading2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.3f, color = primaryColor)
        MdRole.Heading3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.15f, color = primaryColor)
        MdRole.Heading4 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.05f, color = textColor)
        MdRole.Heading5 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize, color = textColor)
        MdRole.Heading6 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 0.9f, color = mutedColor)
        MdRole.Blockquote -> SpanStyle(fontStyle = FontStyle.Italic, color = mutedColor)
        MdRole.ListItem -> SpanStyle(color = textColor)
        else -> SpanStyle(color = textColor)
    }
}

// ── Inline formatting (viewer: clean rendering, no markers shown) ──

private data class InlineMatch(
    val start: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val end: Int,
    val style: SpanStyle
)

private fun findInlineMatches(line: String, accent: Color, highlightBg: Color): List<InlineMatch> {
    val len = line.length
    val result = mutableListOf<InlineMatch>()
    val used = BooleanArray(len)
    var i = 0

    while (i < len) {
        if (used[i]) { i++; continue }
        val m = matchAt(line, i, accent, highlightBg)
        if (m != null && (m.start until m.end).none { used[it] }) {
            result.add(m)
            for (j in m.start until m.end) used[j] = true
            i = m.end
        } else {
            i++
        }
    }
    result.sortBy { it.start }
    return result
}

private fun matchAt(line: String, i: Int, accent: Color, highlightBg: Color): InlineMatch? {
    val len = line.length

    // **bold**
    if (i + 4 < len && line[i] == '*' && line[i + 1] == '*' && line[i + 2] != '*') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '*' && line[e + 1] == '*' && (e + 2 >= len || line[e + 2] != '*'))
                return InlineMatch(i, i + 2, e, e + 2, SpanStyle(fontWeight = FontWeight.Bold, color = accent))
            e++
        }
    }
    // ~~strikethrough~~
    if (i + 4 < len && line[i] == '~' && line[i + 1] == '~') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '~' && line[e + 1] == '~')
                return InlineMatch(i, i + 2, e, e + 2, SpanStyle(textDecoration = TextDecoration.LineThrough, color = accent))
            e++
        }
    }
    // ==highlight==
    if (i + 4 < len && line[i] == '=' && line[i + 1] == '=' && line[i + 2] != '=') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '=' && line[e + 1] == '=' && (e + 2 >= len || line[e + 2] != '='))
                return InlineMatch(i, i + 2, e, e + 2, SpanStyle(background = highlightBg))
            e++
        }
    }
    // <u>underline</u>
    if (i + 6 < len && line[i] == '<' && line[i + 1] == 'u' && line[i + 2] == '>') {
        var e = i + 3
        while (e + 3 < len) {
            if (line[e] == '<' && line[e + 1] == '/' && line[e + 2] == 'u' && line[e + 3] == '>')
                return InlineMatch(i, i + 3, e, e + 4, SpanStyle(textDecoration = TextDecoration.Underline))
            e++
        }
    }
    // `code`
    if (line[i] == '`') {
        var e = i + 1
        while (e < len) {
            if (line[e] == '`')
                return InlineMatch(i, i + 1, e, e + 1,
                    SpanStyle(color = accent, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = highlightBg.copy(alpha = 0.3f)))
            e++
        }
    }
    // *italic*
    if (i + 2 < len && line[i] == '*' && line[i + 1] != '*' && (i == 0 || line[i - 1] != '*')) {
        var e = i + 1
        while (e < len) {
            if (line[e] == '*' && (e + 1 >= len || line[e + 1] != '*') && line[e - 1] != '*')
                return InlineMatch(i, i + 1, e, e + 1, SpanStyle(fontStyle = FontStyle.Italic, color = accent))
            e++
        }
    }
    return null
}

private fun AnnotatedString.Builder.appendInlineHighlighted(
    line: String,
    baseStyle: SpanStyle,
    mutedColor: Color,
    accent: Color,
    highlightBg: Color
) {
    val matches = findInlineMatches(line, accent, highlightBg)
    if (matches.isEmpty()) {
        withStyle(baseStyle) { append(line) }
        return
    }

    var pos = 0
    for (m in matches) {
        // Plain text before match
        if (pos < m.start) {
            withStyle(baseStyle) { append(line.substring(pos, m.start)) }
        }
        // Styled content (markers are stripped in viewer mode)
        withStyle(m.style) { append(line.substring(m.contentStart, m.contentEnd)) }
        pos = m.end
    }
    // Remaining plain text
    if (pos < line.length) {
        withStyle(baseStyle) { append(line.substring(pos)) }
    }
}
