package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.lazydex.data.local.entity.ExtensionRepoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionRepoDao {
    @Query("SELECT * FROM extension_repos ORDER BY name ASC")
    fun getAllReposFlow(): Flow<List<ExtensionRepoEntity>>

    @Query("SELECT * FROM extension_repos ORDER BY name ASC")
    suspend fun getAllRepos(): List<ExtensionRepoEntity>

    @Query("SELECT * FROM extension_repos WHERE id = :id")
    suspend fun getRepoById(id: String): ExtensionRepoEntity?

    @Query("SELECT * FROM extension_repos WHERE manifestUrl = :manifestUrl LIMIT 1")
    suspend fun getRepoByManifestUrl(manifestUrl: String): ExtensionRepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepo(repo: ExtensionRepoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<ExtensionRepoEntity>)

    @Update
    suspend fun updateRepo(repo: ExtensionRepoEntity)

    @Delete
    suspend fun deleteRepo(repo: ExtensionRepoEntity)

    @Query("DELETE FROM extension_repos WHERE id = :id")
    suspend fun deleteRepoById(id: String)
}
