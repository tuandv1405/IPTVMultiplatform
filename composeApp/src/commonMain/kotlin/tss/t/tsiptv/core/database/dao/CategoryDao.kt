package tss.t.tsiptv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tss.t.tsiptv.core.database.entity.CategoryEntity

/**
 * DAO for accessing category data in the database.
 */
@Dao
interface CategoryDao {
    /**
     * Gets all categories.
     *
     * @return A flow of all categories
     */
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /**
     * Gets a category by ID.
     *
     * @param id The ID of the category to get
     * @return The category with the given ID, or null if not found
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    /**
     * Gets categories by playlist.
     *
     * @param playlistId The ID of the playlist to get categories for
     * @return A flow of categories in the given playlist
     */
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId")
    suspend fun getCategoriesByPlaylist(playlistId: String): List<CategoryEntity>

    /**
     * Inserts or updates a category.
     *
     * @param category The category to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    /**
     * Inserts or updates multiple categories.
     *
     * @param categories The categories to insert or update
     */
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    /**
     * Deletes a category.
     *
     * @param category The category to delete
     */
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    /**
     * Deletes a category by ID.
     *
     * @param id The ID of the category to delete
     */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    /**
     * Deletes all categories for a playlist.
     *
     * @param playlistId The ID of the playlist to delete categories for
     */
    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesByPlaylist(playlistId: String)
}