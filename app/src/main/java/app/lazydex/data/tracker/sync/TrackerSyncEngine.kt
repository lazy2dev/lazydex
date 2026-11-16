package app.lazydex.data.tracker.sync

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

interface TrackerSyncEngine {
    val syncProgress: StateFlow<SyncProgress>
    fun launchSync(): Job
    fun cancelSync()
}
