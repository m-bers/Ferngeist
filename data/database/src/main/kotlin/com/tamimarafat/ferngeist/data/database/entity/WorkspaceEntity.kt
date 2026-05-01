package com.tamimarafat.ferngeist.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workspaces",
    indices = [Index(value = ["helperKey", "cwd"], unique = true)],
)
data class WorkspaceEntity(
    @PrimaryKey val workspaceId: String,
    val helperKey: String,
    val cwd: String,
    val displayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
