package com.cynosure.operit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cynosure.operit.data.dao.ChatDao
import com.cynosure.operit.data.dao.MessageDao
import com.cynosure.operit.data.dao.MessageVariantDao
import com.cynosure.operit.data.dao.LanSyncDao
import com.cynosure.operit.data.dao.TaskTraceDao
import com.cynosure.operit.data.dao.ValidationResultDao
import com.cynosure.operit.data.model.ChatEntity
import com.cynosure.operit.data.model.MessageEntity
import com.cynosure.operit.data.model.MessageVariantEntity
import com.cynosure.operit.data.model.TaskTraceEntity
import com.cynosure.operit.data.model.ValidationResultEntity
import com.cynosure.operit.data.model.LanSyncConflictEntity
import com.cynosure.operit.data.model.LanSyncCursorEntity
import com.cynosure.operit.data.model.LanSyncEntityStateEntity
import com.cynosure.operit.data.model.LanSyncJournalEntity
import com.cynosure.operit.data.model.LanSyncPeerEntity

/** 应用数据库，包含聊天表和消息表 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageVariantEntity::class,
        TaskTraceEntity::class,
        ValidationResultEntity::class,
        LanSyncPeerEntity::class,
        LanSyncCursorEntity::class,
        LanSyncEntityStateEntity::class,
        LanSyncJournalEntity::class,
        LanSyncConflictEntity::class,
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao

    abstract fun messageVariantDao(): MessageVariantDao

    abstract fun taskTraceDao(): TaskTraceDao

    abstract fun validationResultDao(): ValidationResultDao

    abstract fun lanSyncDao(): LanSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 定义从版本1到2的迁移
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 创建chats表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `chats` (
                                `id` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`id`)
                            )
                        """.trimIndent()
                    )

                    // 创建messages表
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `messages` (
                                `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `sender` TEXT NOT NULL,
                                `content` TEXT NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `orderIndex` INTEGER NOT NULL,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )

                    // 为messages表创建索引
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
                }

            }

        // 定义从版本10到11的迁移
        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspaceEnv列
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `workspaceEnv` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本11到12的迁移
        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加characterGroupId列（用于绑定群组角色卡）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `characterGroupId` TEXT")
                    } catch (_: Exception) {

                    }
                }
            }

        private val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `inputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `cachedInputTokens` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `sentAt` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `outputDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE messages ADD COLUMN `waitDurationMs` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {
                    }
                }
            }

        private val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `problem_records`")
                }
            }

        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `selectedVariantIndex` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `message_variants` (
                                `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `messageTimestamp` INTEGER NOT NULL,
                                `variantIndex` INTEGER NOT NULL,
                                `content` TEXT NOT NULL,
                                `roleName` TEXT NOT NULL DEFAULT '',
                                `provider` TEXT NOT NULL DEFAULT '',
                                `modelName` TEXT NOT NULL DEFAULT '',
                                `inputTokens` INTEGER NOT NULL DEFAULT 0,
                                `outputTokens` INTEGER NOT NULL DEFAULT 0,
                                `cachedInputTokens` INTEGER NOT NULL DEFAULT 0,
                                `sentAt` INTEGER NOT NULL DEFAULT 0,
                                `outputDurationMs` INTEGER NOT NULL DEFAULT 0,
                                `waitDurationMs` INTEGER NOT NULL DEFAULT 0,
                                FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp` ON `message_variants` (`chatId`, `messageTimestamp`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_variants_chatId_messageTimestamp_variantIndex` ON `message_variants` (`chatId`, `messageTimestamp`, `variantIndex`)"
                    )
                }
            }

        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `displayMode` TEXT NOT NULL DEFAULT 'NORMAL'"
                    )
                }
            }

        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)"
                    )
                }
            }

        private val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE messages ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE message_variants ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0"
                    )
                }
            }

        private val MIGRATION_19_20 =
            object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_20_21 =
            object : Migration(20, 21) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `task_traces` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `chatId` TEXT NOT NULL,
                                `userMessage` TEXT NOT NULL,
                                `assistantReply` TEXT NOT NULL,
                                `toolsUsed` TEXT NOT NULL,
                                `success` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL
                            )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_traces_createdAt` ON `task_traces` (`createdAt`)")
                    db.execSQL(
                        """
                            CREATE TABLE IF NOT EXISTS `validation_results` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `taskTraceId` INTEGER NOT NULL,
                                `toolName` TEXT NOT NULL,
                                `validatedPath` TEXT NOT NULL,
                                `passed` INTEGER NOT NULL,
                                `detail` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                FOREIGN KEY(`taskTraceId`) REFERENCES `task_traces`(`id`) ON DELETE CASCADE
                            )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_validation_results_taskTraceId` ON `validation_results` (`taskTraceId`)")
                    db.execSQL("ALTER TABLE messages ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE messages ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE messages ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE messages ADD COLUMN `deletedAt` INTEGER")
                    db.execSQL(
                        "UPDATE messages SET syncId = lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))), updatedAt = timestamp WHERE syncId = ''"
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_syncId` ON `messages` (`syncId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_updatedAt` ON `messages` (`updatedAt`)")
                }
            }

        private val MIGRATION_21_22 =
            object : Migration(21, 22) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE chats ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE chats ADD COLUMN `deletedAt` INTEGER")
                    db.execSQL("ALTER TABLE message_variants ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE message_variants ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE message_variants ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE message_variants ADD COLUMN `deletedAt` INTEGER")
                    db.execSQL(
                        "UPDATE message_variants SET syncId = lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))), updatedAt = messageTimestamp WHERE syncId = ''"
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_message_variants_syncId` ON `message_variants` (`syncId`)")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `lan_sync_peers` (
                            `deviceId` TEXT NOT NULL,
                            `deviceName` TEXT NOT NULL,
                            `host` TEXT NOT NULL,
                            `port` INTEGER NOT NULL,
                            `outgoingToken` TEXT NOT NULL,
                            `incomingToken` TEXT NOT NULL,
                            `enabledCollections` TEXT NOT NULL,
                            `lastSeenAt` INTEGER NOT NULL,
                            `lastSyncAt` INTEGER NOT NULL,
                            `lastError` TEXT,
                            PRIMARY KEY(`deviceId`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lan_sync_peers_deviceId` ON `lan_sync_peers` (`deviceId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lan_sync_peers_incomingToken` ON `lan_sync_peers` (`incomingToken`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `lan_sync_cursors` (`peerDeviceId` TEXT NOT NULL, `collection` TEXT NOT NULL, `lastSequence` INTEGER NOT NULL, `lastSyncAt` INTEGER NOT NULL, PRIMARY KEY(`peerDeviceId`, `collection`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `lan_sync_entity_state` (`collection` TEXT NOT NULL, `entityId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `payloadHash` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`collection`, `entityId`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lan_sync_entity_state_updatedAt` ON `lan_sync_entity_state` (`updatedAt`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `lan_sync_journal` (`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collection` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `revision` INTEGER NOT NULL, `baseHash` TEXT, `payloadHash` TEXT NOT NULL, `payload` TEXT, `updatedAt` INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lan_sync_journal_collection_sequence` ON `lan_sync_journal` (`collection`, `sequence`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `lan_sync_conflicts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `peerDeviceId` TEXT NOT NULL, `collection` TEXT NOT NULL, `entityId` TEXT NOT NULL, `localRevision` INTEGER NOT NULL, `remoteRevision` INTEGER NOT NULL, `localHash` TEXT, `remoteHash` TEXT NOT NULL, `remotePayload` TEXT, `reason` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `resolvedAt` INTEGER)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lan_sync_conflicts_peerDeviceId_resolvedAt` ON `lan_sync_conflicts` (`peerDeviceId`, `resolvedAt`)")
                }
            }

        private val MIGRATION_22_23 =
            object : Migration(22, 23) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE lan_sync_conflicts ADD COLUMN `remoteSequence` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE lan_sync_conflicts ADD COLUMN `remoteOperation` TEXT NOT NULL DEFAULT 'UPSERT'")
                    db.execSQL("ALTER TABLE lan_sync_conflicts ADD COLUMN `remoteUpdatedAt` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE lan_sync_conflicts ADD COLUMN `resolution` TEXT")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_lan_sync_conflicts_peerDeviceId_collection_entityId_resolvedAt` ON `lan_sync_conflicts` (`peerDeviceId`, `collection`, `entityId`, `resolvedAt`)"
                    )
                }
            }

        // 定义从版本2到3的迁移
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加group列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `group` TEXT")
                }
            }

        // 定义从版本3到4的迁移
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加displayOrder列，并用updatedAt填充现有数据
                    db.execSQL(
                        "ALTER TABLE chats ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("UPDATE chats SET displayOrder = updatedAt")
                }
            }

        // 定义从版本4到5的迁移
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加workspace列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `workspace` TEXT")
                }
            }

        // 定义从版本5到6的迁移
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 检查currentWindowSize列是否已存在，如果不存在则添加
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `currentWindowSize` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        // 定义从版本6到7的迁移
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加roleName列
                    db.execSQL("ALTER TABLE messages ADD COLUMN `roleName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本7到8的迁移
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加parentChatId列
                    db.execSQL("ALTER TABLE chats ADD COLUMN `parentChatId` TEXT")
                    // 向chats表添加characterCardName列（用于绑定角色卡）
                    db.execSQL("ALTER TABLE chats ADD COLUMN `characterCardName` TEXT")
                }
            }

        // 定义从版本8到9的迁移
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向messages表添加provider列（供应商）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `provider` TEXT NOT NULL DEFAULT ''")
                    // 向messages表添加modelName列（模型名称）
                    db.execSQL("ALTER TABLE messages ADD COLUMN `modelName` TEXT NOT NULL DEFAULT ''")
                }
            }

        // 定义从版本9到10的迁移
        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 向chats表添加locked列（锁定聊天，禁止删除）
                    try {
                        db.execSQL("ALTER TABLE chats ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {

                    }
                }
            }

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "app_database"
                        )
                            .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7,
                                MIGRATION_7_8,
                                MIGRATION_8_9,
                                MIGRATION_9_10,
                                MIGRATION_10_11,
                                MIGRATION_11_12,
                                MIGRATION_12_13,
                                MIGRATION_13_14,
                                MIGRATION_14_15,
                                MIGRATION_15_16,
                                MIGRATION_16_17,
                                MIGRATION_17_18,
                                MIGRATION_18_19,
                                MIGRATION_19_20,
                                MIGRATION_20_21,
                                MIGRATION_21_22,
                                MIGRATION_22_23
                            ) // 添加新的迁移
                            .build()
                    INSTANCE = instance
                    instance
                }
        }

        fun closeDatabase() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } finally {
                    INSTANCE = null
                }
            }
        }
    }
}
