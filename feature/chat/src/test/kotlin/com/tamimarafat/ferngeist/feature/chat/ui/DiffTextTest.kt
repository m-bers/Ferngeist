package com.tamimarafat.ferngeist.feature.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffTextTest {

    @Test
    fun `detects standard unified diff with header pair and hunk`() {
        val text = """
            --- a/file.kt
            +++ b/file.kt
            @@ -1,3 +1,3 @@
             unchanged
            -old line
            +new line
        """.trimIndent()
        assertTrue(looksLikeUnifiedDiff(text))
    }

    @Test
    fun `detects when only hunk header is present`() {
        val text = "@@ -1 +1 @@\n-foo\n+bar"
        assertTrue(looksLikeUnifiedDiff(text))
    }

    @Test
    fun `detects unified diff with multiple plus minus lines and no obvious headers`() {
        val text = """
            +line a
            +line b
            -line c
            -line d
             surrounding context
        """.trimIndent()
        assertTrue(looksLikeUnifiedDiff(text))
    }

    @Test
    fun `does not flag plain prose`() {
        val text = "Hello world.\nThis is a story about some files."
        assertFalse(looksLikeUnifiedDiff(text))
    }

    @Test
    fun `does not flag JSON output that contains plus or minus signs`() {
        val text = """{"items": [{"value": -1}, {"value": +2}]}"""
        assertFalse(looksLikeUnifiedDiff(text))
    }

    @Test
    fun `does not flag empty string`() {
        assertFalse(looksLikeUnifiedDiff(""))
        assertFalse(looksLikeUnifiedDiff("   \n  "))
    }
}
