package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.lazydex.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId IS NULL AND deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeTopLevel(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId = :parentId AND deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeTypes(parentId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE parentId = :parentId AND deletedAt IS NULL")
    suspend fun getTypes(parentId: String): List<CategoryEntity>

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM media_items WHERE categoryId = :categoryId")
    suspend fun countDirectMediaItems(categoryId: String): Int

    @Query("""
        SELECT COUNT(*) FROM media_items 
        WHERE categoryId = :categoryId OR categoryId IN (SELECT id FROM categories WHERE parentId = :categoryId)
    """)
    suspend fun countAllMediaItemsIncludingChildren(categoryId: String): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)
}

