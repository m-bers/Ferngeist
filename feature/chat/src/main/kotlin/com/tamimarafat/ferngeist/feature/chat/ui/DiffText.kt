package com.tamimarafat.ferngeist.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Heuristic Zed-parity diff renderer for tool-call output. We don't (yet) get the
 * structured `ToolCallContent::diff` block from the SDK, so we look at the raw text:
 * if it has unified-diff markers (`--- ` / `+++ ` headers, `@@` hunks, or runs of
 * `+`/`-` line prefixes), we render line-by-line with red/green/blue colors. Otherwise
 * we fall back to a plain monospace [Text].
 */
@Composable
internal fun DiffOrPlainText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (looksLikeUnifiedDiff(text)) {
        UnifiedDiffText(text = text, modifier = modifier)
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
            fontFamily = FontFamily.Monospace,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun UnifiedDiffText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        text.split('\n').forEach { line ->
            val color = when {
                line.startsWith("+++") || line.startsWith("---") -> onSurfaceVariant
                line.startsWith("@@") -> Color(0xFF1976D2) // blue, hunk header
                line.startsWith("+") -> Color(0xFF2E7D32) // green
                line.startsWith("-") -> Color(0xFFC62828) // red
                else -> onSurface
            }
            Text(
                text = if (line.isEmpty()) " " else line,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }
    }
}

internal fun looksLikeUnifiedDiff(text: String): Boolean {
    if (text.isBlank()) return false
    // Strong signal: the unified-diff hunk header sequence.
    if (text.contains("\n@@") || text.startsWith("@@")) return true
    // Header-pair signal: "--- a/path" + "+++ b/path".
    if (text.contains("\n--- ") && text.contains("\n+++ ")) return true
    if (text.startsWith("--- ") && text.contains("\n+++ ")) return true
    // Weaker fallback: at least 2 lines starting with + and 2 with - in the first
    // 64 lines, and no line is JSON-looking.
    val lines = text.lineSequence().take(64).toList()
    val plus = lines.count { it.startsWith("+") && !it.startsWith("+++") }
    val minus = lines.count { it.startsWith("-") && !it.startsWith("---") }
    val jsonish = lines.any { it.trimStart().startsWith("{\"") || it.trimStart().startsWith("[{") }
    return !jsonish && plus >= 2 && minus >= 2
}
