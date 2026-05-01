package com.tamimarafat.ferngeist.feature.chat

import com.tamimarafat.ferngeist.core.model.AssistantSegment
import com.tamimarafat.ferngeist.core.model.ChatMessage

/**
 * Renders a chat thread as Markdown for export / share / clipboard. Mirrors Zed's
 * "Open thread as Markdown" feature.
 *
 * Format:
 * - Each user message becomes `## You` followed by the content.
 * - Each assistant message becomes `## Agent` followed by the content + segments.
 * - Tool calls render as `### Tool: <title>` with status/output indented.
 * - Thoughts (reasoning) render as `> reasoning: <text>`.
 * - Plans render as `### Plan` followed by the entry list.
 */
internal object ThreadMarkdown {
    fun render(messages: List<ChatMessage>, sessionTitle: String?): String {
        val sb = StringBuilder()
        sessionTitle?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("# $it").appendLine()
        }
        messages.forEach { message ->
            when (message.role) {
                ChatMessage.Role.USER -> {
                    sb.appendLine("## You").appendLine()
                    if (message.content.isNotBlank()) {
                        sb.appendLine(message.content.trim()).appendLine()
                    }
                    if (message.images.isNotEmpty()) {
                        sb.appendLine("_(×${message.images.size} image attachments)_").appendLine()
                    }
                }
                ChatMessage.Role.ASSISTANT -> {
                    sb.appendLine("## Agent").appendLine()
                    if (message.content.isNotBlank()) {
                        sb.appendLine(message.content.trim()).appendLine()
                    }
                    message.segments.forEach { segment -> appendSegment(sb, segment) }
                }
                ChatMessage.Role.SYSTEM -> {
                    sb.appendLine("> system: ${message.content.trim()}").appendLine()
                }
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun appendSegment(sb: StringBuilder, segment: AssistantSegment) {
        when (segment.kind) {
            AssistantSegment.Kind.MESSAGE -> {
                if (segment.text.isNotBlank()) {
                    sb.appendLine(segment.text.trim()).appendLine()
                }
            }
            AssistantSegment.Kind.THOUGHT -> {
                if (segment.text.isNotBlank()) {
                    sb.appendLine("> reasoning: ${segment.text.trim().replace("\n", "\n> ")}").appendLine()
                }
            }
            AssistantSegment.Kind.TOOL_CALL -> {
                val tool = segment.toolCall ?: return
                val title = tool.title.ifBlank { "Tool" }
                val kind = tool.kind?.let { " ($it)" }.orEmpty()
                val status = tool.status?.let { " — $it" }.orEmpty()
                sb.appendLine("### Tool: $title$kind$status").appendLine()
                tool.output?.takeIf { it.isNotBlank() }?.let {
                    sb.appendLine("```").appendLine(it.trimEnd()).appendLine("```").appendLine()
                }
            }
            AssistantSegment.Kind.PLAN -> {
                if (segment.text.isNotBlank()) {
                    sb.appendLine("### Plan").appendLine()
                    sb.appendLine(segment.text.trim()).appendLine()
                }
            }
        }
    }
}
