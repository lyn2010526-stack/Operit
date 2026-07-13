package com.cynosure.operit.data.backup

import kotlinx.coroutines.sync.Mutex

object RoomDatabaseBackupRestoreLock {
    val mutex = Mutex()
}
