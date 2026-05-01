package com.tamimarafat.ferngeist.feature.chat

import com.tamimarafat.ferngeist.core.model.AssistantSegment
import com.tamimarafat.ferngeist.core.model.ChatImageData
import com.tamimarafat.ferngeist.core.model.ChatMessage
import com.tamimarafat.ferngeist.core.model.ToolCallDisplay
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThreadMarkdownTest {

    @Test
    fun `renders user and agent messages with role headings`() {
        val messages = listOf(
            ChatMessage(role = ChatMessage.Role.USER, content = "Hello?"),
            ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Hi there."),
        )

        val md = ThreadMarkdown.render(messages, sessionTitle = null)

        assertTrue(md.contains("## You"))
        assertTrue(md.contains("Hello?"))
        assertTrue(md.contains("## Agent"))
        assertTrue(md.contains("Hi there."))
    }

    @Test
    fun `renders session title as h1 when present`() {
        val md = ThreadMarkdown.render(emptyList(), sessionTitle = "My thread")
        assertTrue(md.startsWith("# My thread"))
    }

    @Test
    fun `omits session title heading when blank or null`() {
        val mdNull = ThreadMarkdown.render(emptyList(), sessionTitle = null)
        val mdBlank = ThreadMarkdown.render(emptyList(), sessionTitle = "   ")
        assertFalse(mdNull.startsWith("#"))
        assertFalse(mdBlank.startsWith("#"))
    }

    @Test
    fun `renders thought as a quote block prefixed with reasoning`() {
        val message = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            segments = listOf(
                AssistantSegment(
                    kind = AssistantSegment.Kind.THOUGHT,
                    text = "Considering options.",
                )
            ),
        )

        val md = ThreadMarkdown.render(listOf(message), sessionTitle = null)

        assertTrue(md.contains("> reasoning: Considering options."))
    }

    @Test
    fun `renders tool calls with title status and output fenced block`() {
        val message = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            segments = listOf(
                AssistantSegment(
                    kind = AssistantSegment.Kind.TOOL_CALL,
                    toolCall = ToolCallDisplay(
                        title = "Run shell",
                        kind = "execute",
                        status = "completed",
                        output = "hello world",
                    ),
                ),
            ),
        )

        val md = ThreadMarkdown.render(listOf(message), sessionTitle = null)

        assertTrue("expected H3 tool heading", md.contains("### Tool: Run shell"))
        assertTrue("expected kind suffix", md.contains("(execute)"))
        assertTrue("expected status suffix", md.contains("— completed"))
        assertTrue("expected fenced output", md.contains("```\nhello world\n```"))
    }

    @Test
    fun `renders plan segment with H3 plan heading`() {
        val message = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            segments = listOf(
                AssistantSegment(
                    kind = AssistantSegment.Kind.PLAN,
                    text = "1. step one\n2. step two",
                ),
            ),
        )
        val md = ThreadMarkdown.render(listOf(message), sessionTitle = null)
        assertTrue(md.contains("### Plan"))
        assertTrue(md.contains("1. step one"))
    }

    @Test
    fun `notes user image attachment count`() {
        val message = ChatMessage(
            role = ChatMessage.Role.USER,
            content = "see this",
            images = listOf(ChatImageData(base64 = "abc", mimeType = "image/png")),
        )
        val md = ThreadMarkdown.render(listOf(message), sessionTitle = null)
        assertTrue(md.contains("×1 image attachments"))
    }

    @Test
    fun `output ends with newline`() {
        val md = ThreadMarkdown.render(
            listOf(ChatMessage(role = ChatMessage.Role.USER, content = "x")),
            sessionTitle = null,
        )
        assertEquals('\n', md.last())
    }
}
