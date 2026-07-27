# Database Migration: Room v2 → v3

> Schema changes needed to support AniList sync, REPEATING status, extended metadata (including `progressVolumes`), 0-100 Int rating system, and AniList-style local statistics.
> Main plan: [`index.md`](index.md) | Mihon reference: [`mihon-reference.md`](mihon-reference.md) | API reference: [`anilist-api-reference.md`](anilist-api-reference.md)

---

## 1. Summary of Changes

| Change | Type | Impact | Phase |
|--------|------|--------|-------|
| Add `REPEATING` to `UserStatus` & `StatusFilter` | Enum entry | Status dropdowns, filters, DB queries | P2 |
| Add non-colliding `MediaFormat` enum | New Enum | Scoped sub-formats (`TV_SERIES`, `ANIME_MOVIE`, `LIGHT_NOVEL`, `VISUAL_NOVEL`, etc.) | P2 |
| Add 13 Columns to `MediaItemEntity` | New columns | Track sync state, format, duration, total & progress volumes, status, season, adult/doujin flags | P2 |

---

## 2. Domain Model Change: MediaItem.kt

```kotlin
data class MediaItem(
    // ... existing fields ...
    val lastSyncedAt: Long? = null,
    val anilistListEntryId: Long? = null,
    val isPrivate: Boolean = false,
    val mediaFormat: MediaFormat? = null,
    val rawFormat: String? = null,
    val publishingStatus: String? = null,
    val season: String? = null,
    val totalVolumes: Int? = null,
    val progressVolumes: Int? = null,
    val durationMinutes: Int? = null,
    val sourceMaterial: String? = null,
    val isAdult: Boolean = false,
    val isDoujin: Boolean = false,
) {
    fun normalize(): MediaItem {
        return copy(
            lastSyncedAt = lastSyncedAt?.takeIf { it > 0 },
            anilistListEntryId = anilistListEntryId?.takeIf { it > 0 },
            rawFormat = rawFormat?.trim()?.ifBlank { null },
            publishingStatus = publishingStatus?.trim()?.ifBlank { null },
            season = season?.trim()?.ifBlank { null },
            totalVolumes = totalVolumes?.takeIf { it >= 0 },
            progressVolumes = progressVolumes?.takeIf { it >= 0 },
            durationMinutes = durationMinutes?.takeIf { it >= 0 },
            sourceMaterial = sourceMaterial?.trim()?.ifBlank { null },
        )
    }
}
```

---

## 3. Entity Change: MediaItemEntity.kt

```kotlin
@Entity(
    tableName = "media_items",
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
data class MediaItemEntity(
    @ColumnInfo(defaultValue = "NULL") val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val anilistListEntryId: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val mediaFormat: String? = null,
    @ColumnInfo(defaultValue = "NULL") val rawFormat: String? = null,
    @ColumnInfo(defaultValue = "NULL") val publishingStatus: String? = null,
    @ColumnInfo(defaultValue = "NULL") val season: String? = null,
    @ColumnInfo(defaultValue = "NULL") val totalVolumes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val progressVolumes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceMaterial: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDoujin: Boolean = false,
)
```

---

## 4. Migration SQL

```kotlin
const val DATABASE_VERSION = 3

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN lastSyncedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN anilistListEntryId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN mediaFormat TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN rawFormat TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN publishingStatus TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN season TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN totalVolumes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN progressVolumes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN durationMinutes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN sourceMaterial TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isAdult INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isDoujin INTEGER NOT NULL DEFAULT 0")
    }
}
```
