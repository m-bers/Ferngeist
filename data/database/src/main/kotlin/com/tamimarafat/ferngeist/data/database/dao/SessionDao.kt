package com.tamimarafat.ferngeist.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tamimarafat.ferngeist.data.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE serverId = :serverId AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getSessionsByServerId(serverId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE workspaceId = :workspaceId AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getSessionsByWorkspaceId(workspaceId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE workspaceId = :workspaceId AND isArchived = 1 ORDER BY archivedAt DESC")
    fun getArchivedSessionsByWorkspaceId(workspaceId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun getAllArchivedSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("UPDATE sessions SET workspaceId = :workspaceId WHERE sessionId = :sessionId")
    suspend fun setSessionWorkspace(sessionId: String, workspaceId: String)

    @Query("UPDATE sessions SET isArchived = 1, archivedAt = :archivedAt WHERE sessionId = :sessionId")
    suspend fun archiveSession(sessionId: String, archivedAt: Long)

    @Query("UPDATE sessions SET isArchived = 0, archivedAt = NULL WHERE sessionId = :sessionId")
    suspend fun unarchiveSession(sessionId: String)

    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM sessions WHERE serverId = :serverId")
    suspend fun deleteSessionsByServerId(serverId: String)

    @Query("DELETE FROM sessions WHERE workspaceId = :workspaceId")
    suspend fun deleteSessionsByWorkspaceId(workspaceId: String)
}
