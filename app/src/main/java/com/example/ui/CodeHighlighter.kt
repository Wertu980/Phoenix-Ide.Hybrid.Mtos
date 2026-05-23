package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

object CodeHighlighter {

    private val KEYWORDS = setOf(
        "package", "import", "class", "interface", "fun", "val", "var", "return", 
        "if", "else", "when", "for", "while", "break", "continue", "expect", "actual",
        "override", "suspend", "private", "public", "protected", "internal", "data", "object",
        "struct", "var", "let", "func", "import", "struct", "@Composable", "@Database", "@Entity"
    )

    fun highlight(code: String, theme: String): AnnotatedString {
        return buildAnnotatedString {
            // Pick palette depending on active theme
            val keywordColor = when (theme) {
                "Cobalt" -> Color(0xFFFFC600) // yellow
                "Darcula" -> Color(0xFFCC7832) // orange
                else -> Color(0xFFF95B6A) // VS code pinkish-red
            }

            val stringColor = when (theme) {
                "Cobalt" -> Color(0xFF3AD900) // bright green
                "Darcula" -> Color(0xFF6A8759) // olive green
                else -> Color(0xFF98C379) // light sage green
            }

            val commentColor = when (theme) {
                "Cobalt" -> Color(0xFF0088FF)
                "Darcula" -> Color(0xFF808080)
                else -> Color(0xFF7F848E)
            }

            val functionColor = when (theme) {
                "Cobalt" -> Color(0xFF00E0FF)
                "Darcula" -> Color(0xFFFFC66D)
                else -> Color(0xFF61AFEF)
            }

            // Append default text
            append(code)

            // 1. Highlight Single-line Comments
            val lineStarts = mutableListOf<Int>()
            var idx = 0
            while (idx < code.length) {
                if (code.startsWith("//", idx)) {
                    val endOfLine = code.indexOf('\n', idx).let { if (it == -1) code.length else it }
                    addStyle(
                        SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        idx,
                        endOfLine
                    )
                    idx = endOfLine
                } else {
                    idx++
                }
            }

            // 2. Highlight Strings
            idx = 0
            while (idx < code.length) {
                if (code[idx] == '"' && (idx == 0 || code[idx - 1] != '\\')) {
                    val nextQuote = code.indexOf('"', idx + 1)
                    if (nextQuote != -1) {
                        addStyle(
                            SpanStyle(color = stringColor),
                            idx,
                            nextQuote + 1
                        )
                        idx = nextQuote + 1
                        continue
                    }
                }
                idx++
            }

            // 3. Highlight Keywords (word-based matching)
            val pattern = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
            pattern.findAll(code).forEach { result ->
                val word = result.value
                val range = result.range
                
                // Ensure word is not within a comment or string (for simplicity, we can do direct styled range checks if we wanted, but basic overlaps can be dodged check)
                if (KEYWORDS.contains(word)) {
                    addStyle(
                        SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                        range.first,
                        range.last + 1
                    )
                } else if (word.firstOrNull()?.isUpperCase() == true) {
                    // Type or Class highlighting
                    addStyle(
                        SpanStyle(color = Color(0xFFE5C07B)), // Gold type color
                        range.first,
                        range.last + 1
                    )
                } else if (code.getOrNull(range.last + 1) == '(') {
                    // Function name highlighting
                    addStyle(
                        SpanStyle(color = functionColor),
                        range.first,
                        range.last + 1
                    )
                }
            }
        }
    }
}
