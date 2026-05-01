package com.tamimarafat.ferngeist.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceTest {

    @Test
    fun `name falls back to cwd when displayName is null`() {
        val ws = Workspace(
            id = "h|/foo",
            helperKey = "h",
            cwd = "/foo",
            displayName = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertEquals("/foo", ws.name)
    }

    @Test
    fun `name falls back to cwd when displayName is blank`() {
        val ws = Workspace(
            id = "h|/foo",
            helperKey = "h",
            cwd = "/foo",
            displayName = "   ",
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertEquals("/foo", ws.name)
    }

    @Test
    fun `name uses displayName when provided`() {
        val ws = Workspace(
            id = "h|/foo",
            helperKey = "h",
            cwd = "/foo",
            displayName = "ferngeist",
            createdAt = 0L,
            updatedAt = 0L,
        )
        assertEquals("ferngeist", ws.name)
    }
}

class WorkspaceIdsTest {

    @Test
    fun `encode joins helperKey and cwd with a pipe`() {
        assertEquals("helper-1|/Users/me/code", WorkspaceIds.encode("helper-1", "/Users/me/code"))
    }

    @Test
    fun `helperKeyForManual produces stable manual scheme`() {
        assertEquals(
            "manual:wss://example.com:5788",
            WorkspaceIds.helperKeyForManual("wss", "example.com:5788"),
        )
    }
}
