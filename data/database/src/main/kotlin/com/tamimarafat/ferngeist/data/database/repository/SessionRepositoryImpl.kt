package com.tamimarafat.ferngeist.data.database.repository

import com.tamimarafat.ferngeist.core.model.SessionSummary
import com.tamimarafat.ferngeist.core.model.repository.SessionRepository
import com.tamimarafat.ferngeist.data.database.dao.SessionDao
import com.tamimarafat.ferngeist.data.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
) : SessionRepository {

    override fun getSessions(serverId: String): Flow<List<SessionSummary>> {
        return sessionDao.getSessionsByServerId(serverId).map { entities ->
            entities.map(::toSummary)
        }
    }

    override fun getSessionsForWorkspace(workspaceId: String): Flow<List<SessionSummary>> {
        return sessionDao.getSessionsByWorkspaceId(workspaceId).map { entities ->
            entities.map(::toSummary)
        }
    }

    override fun getArchivedSessionsForWorkspace(workspaceId: String): Flow<List<SessionSummary>> {
        return sessionDao.getArchivedSessionsByWorkspaceId(workspaceId).map { entities ->
            entities.map(::toSummary)
        }
    }

    override fun getAllArchivedSessions(): Flow<List<SessionSummary>> {
        return sessionDao.getAllArchivedSessions().map { entities -> entities.map(::toSummary) }
    }

    override suspend fun archiveSession(sessionId: String) {
        sessionDao.archiveSession(sessionId, System.currentTimeMillis())
    }

    override suspend fun unarchiveSession(sessionId: String) {
        sessionDao.unarchiveSession(sessionId)
    }

    override suspend fun upsertSession(serverId: String, summary: SessionSummary) {
        upsertSession(serverId = serverId, workspaceId = null, summary = summary)
    }

    override suspend fun upsertSession(
        serverId: String,
        workspaceId: String?,
        summary: SessionSummary,
    ) {
        val existing = sessionDao.getSessionById(summary.id)
        sessionDao.insertSession(
            SessionEntity(
                sessionId = summary.id,
                serverId = serverId,
                workspaceId = workspaceId ?: existing?.workspaceId,
                title = summary.title,
                cwd = summary.cwd,
                updatedAt = summary.updatedAt,
            )
        )
    }

    override suspend fun deleteSession(serverId: String, sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

    override suspend fun clearSessions(serverId: String) {
        sessionDao.deleteSessionsByServerId(serverId)
    }

    private fun toSummary(entity: SessionEntity): SessionSummary = SessionSummary(
        id = entity.sessionId,
        title = entity.title,
        cwd = entity.cwd,
        updatedAt = entity.updatedAt,
        serverId = entity.serverId,
    )
}
