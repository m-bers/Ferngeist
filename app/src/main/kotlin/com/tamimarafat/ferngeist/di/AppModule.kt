package com.tamimarafat.ferngeist.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tamimarafat.ferngeist.acp.bridge.connection.AcpConnectionRegistry
import com.tamimarafat.ferngeist.acp.bridge.connection.AndroidConnectivityObserver
import com.tamimarafat.ferngeist.core.model.repository.DesktopHelperSourceRepository
import com.tamimarafat.ferngeist.core.model.repository.HelperAgentBindingRepository
import com.tamimarafat.ferngeist.core.model.repository.LaunchableTargetRepository
import com.tamimarafat.ferngeist.core.model.repository.LaunchableTargetSessionSettingsRepository
import com.tamimarafat.ferngeist.core.model.repository.ServerRepository
import com.tamimarafat.ferngeist.core.model.repository.SessionRepository
import com.tamimarafat.ferngeist.core.model.repository.WorkspaceRepository
import com.tamimarafat.ferngeist.data.database.FerngeistDatabase
import com.tamimarafat.ferngeist.data.database.crypto.CredentialEncryptor
import com.tamimarafat.ferngeist.data.database.repository.DesktopHelperSourceRepositoryImpl
import com.tamimarafat.ferngeist.data.database.repository.HelperAgentBindingRepositoryImpl
import com.tamimarafat.ferngeist.data.database.repository.LaunchableTargetRepositoryImpl
import com.tamimarafat.ferngeist.data.database.repository.LaunchableTargetSessionSettingsRepositoryImpl
import com.tamimarafat.ferngeist.data.database.repository.ServerRepositoryImpl
import com.tamimarafat.ferngeist.data.database.repository.SessionRepositoryImpl
import com.tamimarafat.ferngeist.data.database.repository.WorkspaceRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FerngeistDatabase {
        return Room.databaseBuilder(
            context,
            FerngeistDatabase::class.java,
            FerngeistDatabase.DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build()
    }
    
    @Provides
    @Singleton
    fun provideServerRepository(database: FerngeistDatabase, credentialEncryptor: CredentialEncryptor): ServerRepository {
        return ServerRepositoryImpl(database.serverDao(), credentialEncryptor)
    }

    @Provides
    @Singleton
    fun provideDesktopHelperSourceRepository(database: FerngeistDatabase, credentialEncryptor: CredentialEncryptor): DesktopHelperSourceRepository {
        return DesktopHelperSourceRepositoryImpl(database.desktopHelperSourceDao(), credentialEncryptor)
    }

    @Provides
    @Singleton
    fun provideHelperAgentBindingRepository(database: FerngeistDatabase): HelperAgentBindingRepository {
        return HelperAgentBindingRepositoryImpl(database.helperAgentBindingDao())
    }

    @Provides
    @Singleton
    fun provideLaunchableTargetRepository(
        serverRepository: ServerRepository,
        helperSourceRepository: DesktopHelperSourceRepository,
        helperAgentBindingRepository: HelperAgentBindingRepository,
    ): LaunchableTargetRepository {
        return LaunchableTargetRepositoryImpl(
            serverRepository = serverRepository,
            helperSourceRepository = helperSourceRepository,
            helperAgentBindingRepository = helperAgentBindingRepository,
        )
    }
    
    @Provides
    @Singleton
    fun provideSessionRepository(database: FerngeistDatabase): SessionRepository {
        return SessionRepositoryImpl(database.sessionDao())
    }

    @Provides
    @Singleton
    fun provideLaunchableTargetSessionSettingsRepository(database: FerngeistDatabase): LaunchableTargetSessionSettingsRepository {
        return LaunchableTargetSessionSettingsRepositoryImpl(database.launchableTargetSessionSettingsDao())
    }

    @Provides
    @Singleton
    fun provideWorkspaceRepository(database: FerngeistDatabase): WorkspaceRepository {
        return WorkspaceRepositoryImpl(
            workspaceDao = database.workspaceDao(),
            helperAgentBindingDao = database.helperAgentBindingDao(),
            serverDao = database.serverDao(),
            sessionDao = database.sessionDao(),
        )
    }
    
    @Provides
    @Singleton
    fun provideCredentialEncryptor(@ApplicationContext context: Context): CredentialEncryptor {
        return CredentialEncryptor(context)
    }

    @Provides
    @Singleton
    fun provideAcpConnectionRegistry(@ApplicationContext context: Context): AcpConnectionRegistry {
        return AcpConnectionRegistry(
            connectivityObserverFactory = { AndroidConnectivityObserver(context) },
        )
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
        }
    }

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(CIO)
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `servers_new` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `scheme` TEXT NOT NULL,
              `host` TEXT NOT NULL,
              `token` TEXT NOT NULL,
              `workingDirectory` TEXT NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `servers_new` (`id`, `name`, `scheme`, `host`, `token`, `workingDirectory`)
            SELECT `id`, `name`, `scheme`, `host`, `token`, `workingDirectory` FROM `servers`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `servers`")
        db.execSQL("ALTER TABLE `servers_new` RENAME TO `servers`")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `messages`")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `preferredAuthMethodId` TEXT
            """.trimIndent()
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `sourceKind` TEXT NOT NULL DEFAULT 'MANUAL_ACP'
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `helperCredential` TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `helperCredentialExpiresAt` INTEGER
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `helperRemoteMode` TEXT
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `selectedAgentId` TEXT
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `selectedAgentName` TEXT
            """.trimIndent()
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `servers`
            ADD COLUMN `helperSourceId` TEXT
            """.trimIndent()
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `desktop_helper_sources` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `scheme` TEXT NOT NULL,
              `host` TEXT NOT NULL,
              `helperCredential` TEXT NOT NULL,
              `helperCredentialExpiresAt` INTEGER,
              `helperRemoteMode` TEXT,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `desktop_helper_sources` (
              `id`,
              `name`,
              `scheme`,
              `host`,
              `helperCredential`,
              `helperCredentialExpiresAt`,
              `helperRemoteMode`
            )
            SELECT
              `id`,
              `name`,
              `scheme`,
              `host`,
              `helperCredential`,
              `helperCredentialExpiresAt`,
              `helperRemoteMode`
            FROM `servers`
            WHERE `sourceKind` = 'DESKTOP_HELPER' AND (`selectedAgentId` IS NULL OR TRIM(`selectedAgentId`) = '')
            """.trimIndent()
        )
        db.execSQL(
            """
            DELETE FROM `servers`
            WHERE `sourceKind` = 'DESKTOP_HELPER' AND (`selectedAgentId` IS NULL OR TRIM(`selectedAgentId`) = '')
            """.trimIndent()
        )
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `helper_agent_bindings` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `helperSourceId` TEXT NOT NULL,
              `agentId` TEXT NOT NULL,
              `workingDirectory` TEXT NOT NULL,
              `preferredAuthMethodId` TEXT,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`helperSourceId`) REFERENCES `desktop_helper_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_helper_agent_bindings_helperSourceId` ON `helper_agent_bindings` (`helperSourceId`)")
        db.execSQL(
            """
            INSERT INTO `helper_agent_bindings` (
              `id`,
              `name`,
              `helperSourceId`,
              `agentId`,
              `workingDirectory`,
              `preferredAuthMethodId`
            )
            SELECT
              `id`,
              `name`,
              `helperSourceId`,
              `selectedAgentId`,
              `workingDirectory`,
              `preferredAuthMethodId`
            FROM `servers`
            WHERE `sourceKind` = 'DESKTOP_HELPER' AND `helperSourceId` IS NOT NULL AND TRIM(`helperSourceId`) != ''
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `servers_new` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `scheme` TEXT NOT NULL,
              `host` TEXT NOT NULL,
              `token` TEXT NOT NULL,
              `workingDirectory` TEXT NOT NULL,
              `preferredAuthMethodId` TEXT,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `servers_new` (`id`, `name`, `scheme`, `host`, `token`, `workingDirectory`, `preferredAuthMethodId`)
            SELECT `id`, `name`, `scheme`, `host`, `token`, `workingDirectory`, `preferredAuthMethodId`
            FROM `servers`
            WHERE `sourceKind` = 'MANUAL_ACP'
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `servers`")
        db.execSQL("ALTER TABLE `servers_new` RENAME TO `servers`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sessions_new` (
              `sessionId` TEXT NOT NULL,
              `serverId` TEXT NOT NULL,
              `title` TEXT,
              `cwd` TEXT,
              `updatedAt` INTEGER,
              PRIMARY KEY(`sessionId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `sessions_new` (`sessionId`, `serverId`, `title`, `cwd`, `updatedAt`)
            SELECT `sessionId`, `serverId`, `title`, `cwd`, `updatedAt` FROM `sessions`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `sessions`")
        db.execSQL("ALTER TABLE `sessions_new` RENAME TO `sessions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_serverId` ON `sessions` (`serverId`)")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `launchable_target_session_settings` (
              `targetId` TEXT NOT NULL,
              `cwd` TEXT,
              PRIMARY KEY(`targetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `launchable_target_session_settings` (`targetId`, `cwd`)
            SELECT `id`, `workingDirectory` FROM `servers`
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `launchable_target_session_settings` (`targetId`, `cwd`)
            SELECT `id`, `workingDirectory` FROM `helper_agent_bindings`
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `servers_new` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `scheme` TEXT NOT NULL,
              `host` TEXT NOT NULL,
              `token` TEXT NOT NULL,
              `preferredAuthMethodId` TEXT,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `servers_new` (`id`, `name`, `scheme`, `host`, `token`, `preferredAuthMethodId`)
            SELECT `id`, `name`, `scheme`, `host`, `token`, `preferredAuthMethodId` FROM `servers`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `servers`")
        db.execSQL("ALTER TABLE `servers_new` RENAME TO `servers`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `helper_agent_bindings_new` (
              `id` TEXT NOT NULL,
              `name` TEXT NOT NULL,
              `helperSourceId` TEXT NOT NULL,
              `agentId` TEXT NOT NULL,
              `preferredAuthMethodId` TEXT,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`helperSourceId`) REFERENCES `desktop_helper_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_helper_agent_bindings_new_helperSourceId` ON `helper_agent_bindings_new` (`helperSourceId`)")
        db.execSQL(
            """
            INSERT INTO `helper_agent_bindings_new` (`id`, `name`, `helperSourceId`, `agentId`, `preferredAuthMethodId`)
            SELECT `id`, `name`, `helperSourceId`, `agentId`, `preferredAuthMethodId` FROM `helper_agent_bindings`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `helper_agent_bindings`")
        db.execSQL("ALTER TABLE `helper_agent_bindings_new` RENAME TO `helper_agent_bindings`")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Credentials are now encrypted at the repository layer via CredentialEncryptor.
        // No schema changes; existing plaintext credentials remain unencrypted in Room
        // until the corresponding server/helper row is next written (add/update), at
        // which point CredentialEncryptor.encrypt() rewrites them to EncryptedSharedPreferences.
        // Users who never edit a saved server after this migration will retain plaintext
        // credentials in the database backup.
    }
}

/**
 * v11 → v12: Introduce Workspace as the top-level grouping concept (parity with Zed's
 * threads-sidebar IA). Sessions gain a nullable `workspaceId` column. Existing sessions
 * are bucketed by `(helperKey, cwd)`:
 *  - For helper-backed servers (rows in `helper_agent_bindings`): helperKey = the binding's
 *    helperSourceId. Multiple agents on the same helper share a workspace at the same cwd.
 *  - For manual servers (rows in `servers`): helperKey = "manual:<scheme>://<host>".
 *  - For orphans (server rows already deleted but session row lingered): helperKey =
 *    "orphan:<serverId>" so they land in a recoverable workspace rather than being lost.
 *
 * Workspace ids are deterministic: `helperKey || '|' || cwd`. This makes find-or-create
 * idempotent without requiring UUID generation.
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 1. Create the workspaces table with its (helperKey, cwd) unique index.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workspaces` (
              `workspaceId` TEXT NOT NULL,
              `helperKey` TEXT NOT NULL,
              `cwd` TEXT NOT NULL,
              `displayName` TEXT,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              PRIMARY KEY(`workspaceId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_helperKey_cwd` " +
                "ON `workspaces` (`helperKey`, `cwd`)"
        )

        // 2. Populate workspaces from distinct (helperKey, cwd) tuples derived from sessions.
        //    For helper-backed sessions, helperKey comes from helper_agent_bindings;
        //    for manual sessions, from servers; otherwise we synthesize an orphan key.
        db.execSQL(
            """
            INSERT OR IGNORE INTO `workspaces`
                (`workspaceId`, `helperKey`, `cwd`, `displayName`, `createdAt`, `updatedAt`)
            SELECT
                CASE
                    WHEN h.`helperSourceId` IS NOT NULL
                        THEN h.`helperSourceId` || '|' || COALESCE(s.`cwd`, '/')
                    WHEN sv.`id` IS NOT NULL
                        THEN 'manual:' || sv.`scheme` || '://' || sv.`host` || '|' || COALESCE(s.`cwd`, '/')
                    ELSE
                        'orphan:' || s.`serverId` || '|' || COALESCE(s.`cwd`, '/')
                END AS `workspaceId`,
                CASE
                    WHEN h.`helperSourceId` IS NOT NULL THEN h.`helperSourceId`
                    WHEN sv.`id` IS NOT NULL THEN 'manual:' || sv.`scheme` || '://' || sv.`host`
                    ELSE 'orphan:' || s.`serverId`
                END AS `helperKey`,
                COALESCE(s.`cwd`, '/') AS `cwd`,
                NULL AS `displayName`,
                $now AS `createdAt`,
                $now AS `updatedAt`
            FROM `sessions` s
            LEFT JOIN `helper_agent_bindings` h ON h.`id` = s.`serverId`
            LEFT JOIN `servers` sv ON sv.`id` = s.`serverId`
            GROUP BY `workspaceId`
            """.trimIndent()
        )

        // 3. Rebuild `sessions` with a `workspaceId` column. SQLite doesn't support
        //    ALTER TABLE ADD COLUMN ... DEFAULT (subquery), so we use the create-new-rename
        //    dance to populate from a JOIN.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sessions_new` (
              `sessionId` TEXT NOT NULL,
              `serverId` TEXT NOT NULL,
              `workspaceId` TEXT,
              `title` TEXT,
              `cwd` TEXT,
              `updatedAt` INTEGER,
              PRIMARY KEY(`sessionId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `sessions_new`
                (`sessionId`, `serverId`, `workspaceId`, `title`, `cwd`, `updatedAt`)
            SELECT
                s.`sessionId`,
                s.`serverId`,
                CASE
                    WHEN h.`helperSourceId` IS NOT NULL
                        THEN h.`helperSourceId` || '|' || COALESCE(s.`cwd`, '/')
                    WHEN sv.`id` IS NOT NULL
                        THEN 'manual:' || sv.`scheme` || '://' || sv.`host` || '|' || COALESCE(s.`cwd`, '/')
                    ELSE
                        'orphan:' || s.`serverId` || '|' || COALESCE(s.`cwd`, '/')
                END AS `workspaceId`,
                s.`title`,
                s.`cwd`,
                s.`updatedAt`
            FROM `sessions` s
            LEFT JOIN `helper_agent_bindings` h ON h.`id` = s.`serverId`
            LEFT JOIN `servers` sv ON sv.`id` = s.`serverId`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `sessions`")
        db.execSQL("ALTER TABLE `sessions_new` RENAME TO `sessions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_serverId` ON `sessions` (`serverId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_workspaceId` ON `sessions` (`workspaceId`)")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `launchable_target_session_settings_new` (
              `targetId` TEXT NOT NULL,
              `cwd` TEXT,
              PRIMARY KEY(`targetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `launchable_target_session_settings_new` (`targetId`, `cwd`)
            SELECT `targetId`, `cwd` FROM `launchable_target_session_settings`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `launchable_target_session_settings`")
        db.execSQL("ALTER TABLE `launchable_target_session_settings_new` RENAME TO `launchable_target_session_settings`")
    }
}
