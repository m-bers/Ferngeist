package com.tamimarafat.ferngeist.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tamimarafat.ferngeist.data.database.dao.HelperAgentBindingDao
import com.tamimarafat.ferngeist.data.database.dao.DesktopHelperSourceDao
import com.tamimarafat.ferngeist.data.database.dao.LaunchableTargetSessionSettingsDao
import com.tamimarafat.ferngeist.data.database.dao.ServerDao
import com.tamimarafat.ferngeist.data.database.dao.SessionDao
import com.tamimarafat.ferngeist.data.database.dao.WorkspaceDao
import com.tamimarafat.ferngeist.data.database.entity.DesktopHelperSourceEntity
import com.tamimarafat.ferngeist.data.database.entity.HelperAgentBindingEntity
import com.tamimarafat.ferngeist.data.database.entity.LaunchableTargetSessionSettingsEntity
import com.tamimarafat.ferngeist.data.database.entity.ServerEntity
import com.tamimarafat.ferngeist.data.database.entity.SessionEntity
import com.tamimarafat.ferngeist.data.database.entity.WorkspaceEntity

@Database(
    entities = [
        ServerEntity::class,
        DesktopHelperSourceEntity::class,
        HelperAgentBindingEntity::class,
        SessionEntity::class,
        LaunchableTargetSessionSettingsEntity::class,
        WorkspaceEntity::class,
    ],
    version = 13,
    exportSchema = false
)
abstract class FerngeistDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun desktopHelperSourceDao(): DesktopHelperSourceDao
    abstract fun helperAgentBindingDao(): HelperAgentBindingDao
    abstract fun sessionDao(): SessionDao
    abstract fun launchableTargetSessionSettingsDao(): LaunchableTargetSessionSettingsDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        const val DATABASE_NAME = "ferngeist_database"
    }
}
