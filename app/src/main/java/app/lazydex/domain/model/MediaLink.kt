package app.lazydex.domain.model

data class MediaLink(
    val id: String,
    val mediaId: String,
    val kind: String, // 'SOURCE', 'METADATA', 'TRACKER'
    val providerId: String,
    val remoteId: String? = null,
    val url: String? = null,
    val state: String = "LINKED", // 'LINKED', 'PENDING_MATCH', 'NOT_FOUND', 'SKIPPED'
    val lastSyncedAt: Long? = null,
    val syncedSnapshot: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

