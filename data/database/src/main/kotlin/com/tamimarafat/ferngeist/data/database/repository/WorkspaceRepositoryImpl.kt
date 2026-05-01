package com.tamimarafat.ferngeist.data.database.repository

import com.tamimarafat.ferngeist.core.model.Workspace
import com.tamimarafat.ferngeist.core.model.WorkspaceIds
import com.tamimarafat.ferngeist.core.model.repository.WorkspaceRepository
import com.tamimarafat.ferngeist.data.database.dao.HelperAgentBindingDao
import com.tamimarafat.ferngeist.data.database.dao.ServerDao
import com.tamimarafat.ferngeist.data.database.dao.SessionDao
import com.tamimarafat.ferngeist.data.database.dao.WorkspaceDao
import com.tamimarafat.ferngeist.data.database.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkspaceRepositoryImpl(
    private val workspaceDao: WorkspaceDao,
    private val helperAgentBindingDao: HelperAgentBindingDao,
    private val serverDao: ServerDao,
    private val sessionDao: SessionDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : WorkspaceRepository {

    override fun getAllWorkspaces(): Flow<List<Workspace>> {
        return workspaceDao.getAllWorkspaces().map { entities -> entities.map(::toDomain) }
    }

    override suspend fun findByKey(helperKey: String, cwd: String): Workspace? {
        return workspaceDao.findByHelperAndCwd(helperKey, cwd)?.let(::toDomain)
    }

    override suspend fun getById(id: String): Workspace? {
        return workspaceDao.getWorkspaceById(id)?.let(::toDomain)
    }

    override suspend fun findOrCreate(helperKey: String, cwd: String): Workspace {
        workspaceDao.findByHelperAndCwd(helperKey, cwd)?.let { return toDomain(it) }
        val timestamp = now()
        val entity = WorkspaceEntity(
            workspaceId = WorkspaceIds.encode(helperKey, cwd),
            helperKey = helperKey,
            cwd = cwd,
            displayName = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        workspaceDao.insertWorkspace(entity)
        return toDomain(entity)
    }

    override suspend fun helperKeyForServer(serverId: String): String? {
        helperAgentBindingDao.getBindingById(serverId)?.let { return it.helperSourceId }
        serverDao.getServerById(serverId)?.let {
            return WorkspaceIds.helperKeyForManual(it.scheme, it.host)
        }
        return null
    }

    override suspend fun findOrCreateForServer(serverId: String, cwd: String): Workspace? {
        val helperKey = helperKeyForServer(serverId) ?: return null
        return findOrCreate(helperKey, cwd)
    }

    override suspend fun rename(workspaceId: String, displayName: String?) {
        workspaceDao.renameWorkspace(workspaceId, displayName, now())
    }

    override suspend fun touch(workspaceId: String) {
        workspaceDao.touchWorkspace(workspaceId, now())
    }

    override suspend fun delete(workspaceId: String, cascadeSessions: Boolean) {
        if (cascadeSessions) {
            sessionDao.deleteSessionsByWorkspaceId(workspaceId)
        }
        workspaceDao.deleteWorkspaceById(workspaceId)
    }

    private fun toDomain(entity: WorkspaceEntity): Workspace = Workspace(
        id = entity.workspaceId,
        helperKey = entity.helperKey,
        cwd = entity.cwd,
        displayName = entity.displayName,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )
}
