package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.lazydex.data.local.entity.InstalledExtensionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledExtensionDao {
    @Query("SELECT * FROM installed_extensions ORDER BY name ASC")
    fun getAllInstalledFlow(): Flow<List<InstalledExtensionEntity>>

    @Query("SELECT * FROM installed_extensions ORDER BY name ASC")
    suspend fun getAllInstalled(): List<InstalledExtensionEntity>

    @Query("SELECT * FROM installed_extensions WHERE pkgName = :pkgName LIMIT 1")
    suspend fun getExtension(pkgName: String): InstalledExtensionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: InstalledExtensionEntity)

    @Update
    suspend fun updateExtension(extension: InstalledExtensionEntity)

    @Delete
    suspend fun deleteExtension(extension: InstalledExtensionEntity)

    @Query("DELETE FROM installed_extensions WHERE pkgName = :pkgName")
    suspend fun deleteExtensionByPkg(pkgName: String)

    @Query("UPDATE installed_extensions SET categoryOverride = :categoryOverride WHERE pkgName = :pkgName")
    suspend fun updateCategoryOverride(pkgName: String, categoryOverride: String?)
}
