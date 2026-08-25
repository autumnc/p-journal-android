package com.pjournal.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
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
    highlightBg: Color = Color.Transparent,
    einkMode: Boolean = false,
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
                withStyle(SpanStyle(
                    color = primaryColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = if (einkMode) FontWeight.Bold else null
                )) {
                    append(line)
                }
                continue
            }

            if (inCodeBlock) {
                withStyle(SpanStyle(
                    color = accentColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    background = if (einkMode) highlightBg else Color.Transparent
                )) {
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
                MdRole.Blockquote -> SpanStyle(color = primaryColor, fontWeight = if (einkMode) FontWeight.Bold else null)
                MdRole.ListItem -> SpanStyle(color = primaryColor, fontWeight = if (einkMode) FontWeight.Bold else null)
                else -> SpanStyle(color = textColor)
            }
            val headingContentStyle = when (role) {
                MdRole.Heading1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.5f, color = textColor)
                MdRole.Heading2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.3f, color = textColor)
                MdRole.Heading3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.15f, color = textColor)
                MdRole.Heading4 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.05f, color = textColor)
                MdRole.Heading5 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize, color = textColor)
                MdRole.Heading6 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 0.9f, color = mutedColor)
                MdRole.Blockquote -> SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = if (einkMode) textColor else mutedColor,
                    background = if (einkMode) highlightBg else Color.Transparent
                )
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
                    appendInlineWithMarkers(rest, headingContentStyle, primaryColor, accentColor, highlightBg, einkMode)
                }
            } else {
                // Normal line: apply inline highlighting keeping markers
                appendInlineWithMarkers(line, SpanStyle(color = textColor), primaryColor, accentColor, highlightBg, einkMode)
            }
        }
    }
}

fun renderMarkdownForEditor(
    text: String,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    accentColor: Color,
    highlightBg: Color = Color.Transparent,
    einkMode: Boolean = false,
    baseFontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    customFontBoldBoost: Boolean = false
): TransformedText {
    val sourceToTransformed = IntArray(text.length + 1)
    val transformedToSource = mutableListOf<Int>()

    val rendered = buildAnnotatedString {
        val lines = text.split('\n')
        var sourceLineStart = 0
        var inCodeBlock = false

        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) {
                appendMappedText(
                    value = "\n",
                    sourceStart = sourceLineStart - 1,
                    style = SpanStyle(color = textColor),
                    sourceToTransformed = sourceToTransformed,
                    transformedToSource = transformedToSource
                )
            }

            val trimmed = line.trimStart()
            val leadingWs = line.length - trimmed.length
            val lineStart = sourceLineStart

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                val fenceStart = lineStart + leadingWs
                hideSourceRange(fenceStart, lineStart + line.length, sourceToTransformed, length)
                sourceLineStart += line.length + 1
                continue
            }

            if (inCodeBlock) {
                appendMappedRange(
                    source = text,
                    start = lineStart,
                    end = lineStart + line.length,
                    style = SpanStyle(
                        color = if (einkMode) textColor else accentColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = highlightBg
                    ),
                    sourceToTransformed = sourceToTransformed,
                    transformedToSource = transformedToSource
                )
                sourceLineStart += line.length + 1
                continue
            }

            if (leadingWs > 0) {
                appendMappedRange(
                    source = text,
                    start = lineStart,
                    end = lineStart + leadingWs,
                    style = SpanStyle(color = textColor),
                    sourceToTransformed = sourceToTransformed,
                    transformedToSource = transformedToSource
                )
            }

            val (role, prefixLen) = detectBlockRole(trimmed)
            val prefixStart = lineStart + leadingWs
            val contentStart = prefixStart + prefixLen
            val lineEnd = lineStart + line.length
            val blockStyle = editorRenderedBlockStyle(role, textColor, mutedColor, primaryColor, highlightBg, einkMode, baseFontSize)

            if (prefixLen > 0) {
                hideSourceRange(prefixStart, contentStart, sourceToTransformed, length)
                when (role) {
                    MdRole.Blockquote -> appendInsertedText(
                        value = "▌ ",
                        sourceOffset = prefixStart,
                        style = SpanStyle(
                            color = if (einkMode) Color.Black else primaryColor,
                            fontWeight = FontWeight.Black
                        ),
                        transformedToSource = transformedToSource
                    )
                    MdRole.ListItem -> appendInsertedText(
                        value = "• ",
                        sourceOffset = prefixStart,
                        style = SpanStyle(
                            color = if (einkMode) Color.Black else primaryColor,
                            fontWeight = FontWeight.Bold
                        ),
                        transformedToSource = transformedToSource
                    )
                    else -> Unit
                }
                appendEditorRenderedInline(
                    source = text,
                    start = contentStart,
                    end = lineEnd,
                    baseStyle = blockStyle,
                    markerColor = primaryColor,
                    accentColor = accentColor,
                    highlightBg = highlightBg,
                    einkMode = einkMode,
                    customFontBoldBoost = customFontBoldBoost,
                    sourceToTransformed = sourceToTransformed,
                    transformedToSource = transformedToSource
                )
            } else {
                appendEditorRenderedInline(
                    source = text,
                    start = lineStart,
                    end = lineEnd,
                    baseStyle = blockStyle,
                    markerColor = primaryColor,
                    accentColor = accentColor,
                    highlightBg = highlightBg,
                    einkMode = einkMode,
                    customFontBoldBoost = customFontBoldBoost,
                    sourceToTransformed = sourceToTransformed,
                    transformedToSource = transformedToSource
                )
            }

            sourceLineStart += line.length + 1
        }
    }

    sourceToTransformed[text.length] = rendered.length
    transformedToSource.add(text.length)

    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            return sourceToTransformed[offset.coerceIn(0, sourceToTransformed.lastIndex)]
                .coerceIn(0, rendered.length)
        }

        override fun transformedToOriginal(offset: Int): Int {
            return transformedToSource[offset.coerceIn(0, transformedToSource.lastIndex)]
                .coerceIn(0, text.length)
        }
    }

    return TransformedText(rendered, offsetMapping)
}

private fun AnnotatedString.Builder.appendMappedText(
    value: String,
    sourceStart: Int,
    style: SpanStyle,
    sourceToTransformed: IntArray,
    transformedToSource: MutableList<Int>
) {
    withStyle(style) {
        for (i in value.indices) {
            val sourceOffset = sourceStart + i
            if (sourceOffset in sourceToTransformed.indices) {
                sourceToTransformed[sourceOffset] = length
            }
            transformedToSource.add(sourceOffset.coerceAtLeast(0))
            append(value[i])
        }
    }
}

private fun AnnotatedString.Builder.appendInsertedText(
    value: String,
    sourceOffset: Int,
    style: SpanStyle,
    transformedToSource: MutableList<Int>
) {
    withStyle(style) {
        for (char in value) {
            transformedToSource.add(sourceOffset)
            append(char)
        }
    }
}

private fun AnnotatedString.Builder.appendMappedRange(
    source: String,
    start: Int,
    end: Int,
    style: SpanStyle,
    sourceToTransformed: IntArray,
    transformedToSource: MutableList<Int>
) {
    if (start >= end) return
    withStyle(style) {
        for (sourceOffset in start until end) {
            sourceToTransformed[sourceOffset] = length
            transformedToSource.add(sourceOffset)
            append(source[sourceOffset])
        }
    }
}

private fun hideSourceRange(
    start: Int,
    end: Int,
    sourceToTransformed: IntArray,
    transformedOffset: Int
) {
    for (sourceOffset in start until end) {
        if (sourceOffset in sourceToTransformed.indices) {
            sourceToTransformed[sourceOffset] = transformedOffset
        }
    }
}

private fun editorRenderedBlockStyle(
    role: MdRole,
    textColor: Color,
    mutedColor: Color,
    primaryColor: Color,
    highlightBg: Color,
    einkMode: Boolean,
    baseFontSize: androidx.compose.ui.unit.TextUnit
): SpanStyle {
    return when (role) {
        MdRole.Heading1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.5f, color = primaryColor)
        MdRole.Heading2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.3f, color = primaryColor)
        MdRole.Heading3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.15f, color = primaryColor)
        MdRole.Heading4 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 1.05f, color = textColor)
        MdRole.Heading5 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize, color = textColor)
        MdRole.Heading6 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseFontSize * 0.9f, color = mutedColor)
        MdRole.Blockquote -> SpanStyle(
            fontStyle = FontStyle.Italic,
            color = if (einkMode) Color.Black else mutedColor,
            background = if (einkMode) highlightBg else Color.Transparent
        )
        else -> SpanStyle(color = textColor)
    }
}

private fun AnnotatedString.Builder.appendEditorRenderedInline(
    source: String,
    start: Int,
    end: Int,
    baseStyle: SpanStyle,
    markerColor: Color,
    accentColor: Color,
    highlightBg: Color,
    einkMode: Boolean,
    customFontBoldBoost: Boolean,
    sourceToTransformed: IntArray,
    transformedToSource: MutableList<Int>
) {
    if (start >= end) return

    val line = source.substring(start, end)
    var pos = 0
    while (pos < line.length) {
        val match = editorMatchAt(line, pos, markerColor, accentColor, highlightBg, einkMode, customFontBoldBoost)
        if (match == null) {
            appendMappedRange(source, start + pos, start + pos + 1, baseStyle, sourceToTransformed, transformedToSource)
            pos++
            continue
        }

        hideSourceRange(
            start + match.markerStart,
            start + match.markerEnd,
            sourceToTransformed,
            length
        )
        appendMappedRange(
            source,
            start + match.contentStart,
            start + match.contentEnd,
            match.contentStyle,
            sourceToTransformed,
            transformedToSource
        )
        hideSourceRange(
            start + match.endMarkerStart,
            start + match.endMarkerEnd,
            sourceToTransformed,
            length
        )
        pos = match.end
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

private fun findEditorInlineMatches(
    line: String,
    primaryColor: Color,
    accentColor: Color,
    highlightBg: Color,
    einkMode: Boolean
): List<EditorInlineMatch> {
    val len = line.length
    val result = mutableListOf<EditorInlineMatch>()
    val used = BooleanArray(len)
    var i = 0

    while (i < len) {
        if (used[i]) { i++; continue }
        val m = editorMatchAt(line, i, primaryColor, accentColor, highlightBg, einkMode)
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

private fun editorMatchAt(
    line: String,
    i: Int,
    primaryColor: Color,
    accentColor: Color,
    highlightBg: Color,
    einkMode: Boolean,
    customFontBoldBoost: Boolean = false
): EditorInlineMatch? {
    val len = line.length
    val markerStyle = SpanStyle(
        color = primaryColor,
        fontWeight = if (einkMode) FontWeight.Black else null,
        background = if (einkMode) highlightBg else Color.Transparent
    )
    val monoStyle = SpanStyle(
        color = accentColor,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        background = highlightBg
    )

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
                    contentStyle = SpanStyle(
                        fontWeight = if (einkMode) FontWeight.Black else FontWeight.Bold,
                        fontSynthesis = FontSynthesis.All,
                        textGeometricTransform = TextGeometricTransform(
                            scaleX = when {
                                customFontBoldBoost && einkMode -> 1.2f
                                customFontBoldBoost -> 1.14f
                                einkMode -> 1.12f
                                else -> 1.06f
                            }
                        ),
                        shadow = Shadow(
                            color = if (einkMode) Color.Black else accentColor,
                            offset = Offset(
                                when {
                                    customFontBoldBoost && einkMode -> 1.05f
                                    customFontBoldBoost -> 0.8f
                                    einkMode -> 0.7f
                                    else -> 0.45f
                                },
                                0f
                            ),
                            blurRadius = 0f
                        ),
                        color = if (einkMode) Color.Black else accentColor
                    )
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
                    contentStyle = SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = if (einkMode) Color.Black else accentColor,
                        fontWeight = if (einkMode) FontWeight.SemiBold else null
                    )
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
                    contentStyle = SpanStyle(
                        color = if (einkMode) Color.White else accentColor,
                        fontWeight = if (einkMode) FontWeight.Bold else FontWeight.SemiBold,
                        fontSynthesis = FontSynthesis.All,
                        background = if (einkMode) Color.Black else highlightBg
                    )
                )
            }
            e++
        }
    }
    // <u>underline</u>
    underlineOpenEnd(line, i)?.let { openEnd ->
        val closeStart = line.indexOf("</u>", startIndex = openEnd, ignoreCase = true)
        if (closeStart > openEnd) {
                return EditorInlineMatch(
                    start = i, markerStart = i, markerEnd = openEnd,
                    contentStart = openEnd, contentEnd = closeStart,
                    endMarkerStart = closeStart, endMarkerEnd = closeStart + 4,
                    end = closeStart + 4,
                    markerStyle = markerStyle,
                    contentStyle = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = if (einkMode) Color.Black else accentColor,
                        fontWeight = if (einkMode) FontWeight.SemiBold else null
                    )
                )
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
                    contentStyle = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        fontSynthesis = FontSynthesis.All,
                        textGeometricTransform = TextGeometricTransform(skewX = -0.25f),
                        color = if (einkMode) Color.Black else accentColor,
                        background = if (einkMode) Color.Transparent else Color.Transparent
                    )
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
    accentColor: Color,
    highlightBg: Color,
    einkMode: Boolean
) {
    val matches = findEditorInlineMatches(line, primaryColor, accentColor, highlightBg, einkMode)
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
    einkMode: Boolean = false,
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
                withStyle(SpanStyle(
                    color = mutedColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = if (einkMode) FontWeight.Bold else null
                )) {
                    append(trimmed)
                }
                continue
            }

            if (inCodeBlock) {
                withStyle(SpanStyle(
                    color = accentYellow,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    background = if (einkMode) highlightBg else Color.Transparent
                )) {
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
                    when (role) {
                        MdRole.Blockquote -> {
                            withStyle(SpanStyle(color = mutedColor, fontWeight = FontWeight.Bold)) { append("> ") }
                            appendInlineHighlighted(rest, baseStyle, mutedColor, accentYellow, highlightBg, einkMode)
                        }
                        MdRole.ListItem -> {
                            withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) { append("- ") }
                            appendInlineHighlighted(rest, baseStyle, mutedColor, accentYellow, highlightBg, einkMode)
                        }
                        else -> appendInlineHighlighted(rest, baseStyle, mutedColor, accentYellow, highlightBg, einkMode)
                    }
                }
            } else {
                appendInlineHighlighted(line, baseStyle, mutedColor, accentYellow, highlightBg, einkMode)
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
    val style: SpanStyle,
    val addEmphasisDots: Boolean = false
)

private fun findInlineMatches(line: String, accent: Color, highlightBg: Color, einkMode: Boolean): List<InlineMatch> {
    val len = line.length
    val result = mutableListOf<InlineMatch>()
    val used = BooleanArray(len)
    var i = 0

    while (i < len) {
        if (used[i]) { i++; continue }
        val m = matchAt(line, i, accent, highlightBg, einkMode)
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

private fun matchAt(line: String, i: Int, accent: Color, highlightBg: Color, einkMode: Boolean): InlineMatch? {
    val len = line.length

    // **bold**
    if (i + 4 < len && line[i] == '*' && line[i + 1] == '*' && line[i + 2] != '*') {
        var e = i + 2
        while (e + 1 < len) {
            if (line[e] == '*' && line[e + 1] == '*' && (e + 2 >= len || line[e + 2] != '*'))
                return InlineMatch(
                    i,
                    i + 2,
                    e,
                    e + 2,
                    SpanStyle(
                        fontWeight = if (einkMode) FontWeight.Black else FontWeight.Bold,
                        fontSynthesis = FontSynthesis.All,
                        textGeometricTransform = TextGeometricTransform(scaleX = if (einkMode) 1.12f else 1.06f),
                        shadow = Shadow(
                            color = accent,
                            offset = Offset(if (einkMode) 0.7f else 0.45f, 0f),
                            blurRadius = 0f
                        ),
                        color = accent
                    )
                )
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
                return InlineMatch(
                    i,
                    i + 2,
                    e,
                    e + 2,
                    SpanStyle(
                        color = if (einkMode) Color.White else Color.Unspecified,
                        fontWeight = if (einkMode) FontWeight.Bold else null,
                        fontSynthesis = FontSynthesis.All,
                        background = if (einkMode) Color.Black else highlightBg
                    )
                )
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
                return InlineMatch(
                    i,
                    i + 1,
                    e,
                    e + 1,
                    SpanStyle(
                        fontStyle = FontStyle.Italic,
                        fontSynthesis = FontSynthesis.All,
                        textGeometricTransform = TextGeometricTransform(skewX = -0.25f),
                        color = accent
                    )
                )
            e++
        }
    }
    return null
}

private fun underlineOpenEnd(line: String, start: Int): Int? {
    if (start + 2 >= line.length || line[start] != '<' || line[start + 1].lowercaseChar() != 'u') {
        return null
    }
    var pos = start + 2
    while (pos < line.length && line[pos].isWhitespace()) pos++
    return if (pos < line.length && line[pos] == '>') pos + 1 else null
}

private fun AnnotatedString.Builder.appendInlineHighlighted(
    line: String,
    baseStyle: SpanStyle,
    mutedColor: Color,
    accent: Color,
    highlightBg: Color,
    einkMode: Boolean
) {
    val matches = findInlineMatches(line, accent, highlightBg, einkMode)
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
        val content = line.substring(m.contentStart, m.contentEnd)
        withStyle(m.style) {
            append(if (m.addEmphasisDots) content.withBottomDots() else content)
        }
        pos = m.end
    }
    // Remaining plain text
    if (pos < line.length) {
        withStyle(baseStyle) { append(line.substring(pos)) }
    }
}

private fun String.withBottomDots(): String {
    return buildString {
        for (char in this@withBottomDots) {
            append(char)
            if (!char.isWhitespace()) append('\u0323')
        }
    }
}
