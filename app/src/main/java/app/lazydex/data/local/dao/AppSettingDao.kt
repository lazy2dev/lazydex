package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.lazydex.data.local.entity.AppSettingEntity

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun set(setting: AppSettingEntity)

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettingEntity>
}

