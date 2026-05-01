package com.tamimarafat.ferngeist.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tamimarafat.ferngeist.data.database.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY updatedAt DESC")
    fun getAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE workspaceId = :id")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE helperKey = :helperKey AND cwd = :cwd LIMIT 1")
    suspend fun findByHelperAndCwd(helperKey: String, cwd: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity)

    @Update
    suspend fun updateWorkspace(workspace: WorkspaceEntity)

    @Query("UPDATE workspaces SET displayName = :displayName, updatedAt = :updatedAt WHERE workspaceId = :id")
    suspend fun renameWorkspace(id: String, displayName: String?, updatedAt: Long)

    @Query("UPDATE workspaces SET updatedAt = :updatedAt WHERE workspaceId = :id")
    suspend fun touchWorkspace(id: String, updatedAt: Long)

    @Query("DELETE FROM workspaces WHERE workspaceId = :id")
    suspend fun deleteWorkspaceById(id: String)
}
