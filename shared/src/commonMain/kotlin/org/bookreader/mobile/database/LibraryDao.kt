package org.bookreader.mobile.database

import androidx.room.*

@Entity(tableName = "library_book_details", foreignKeys = [ForeignKey(entity = BookEntity::class,
    parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class LibraryBookDetailsEntity(@PrimaryKey val bookId: String, val metadataEdits: Int = 0,
    val metadataScanned: Boolean = false, val readingStatus: String? = null, val coverPreset: Int = 0)

@Entity(tableName = "library_shelves", indices = [Index(value = ["nameKey"], unique = true)])
data class ShelfEntity(@PrimaryKey val id: String, val name: String, val nameKey: String)

@Entity(tableName = "library_memberships", primaryKeys = ["bookId", "shelfId"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ShelfEntity::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["shelfId"])])
data class ShelfMembershipEntity(val bookId: String, val shelfId: String)

@Entity(tableName = "library_preferences")
data class LibraryPreferencesEntity(@PrimaryKey val id: String = "global", val sort: String = "RECENT",
    val view: String = "GRID", val filter: String = "ALL", val shelfId: String? = null)

data class LibraryProgressRow(val bookId: String, val percent: Double?)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_book_details") suspend fun details(): List<LibraryBookDetailsEntity>
    @Query("SELECT * FROM library_book_details WHERE bookId = :id") suspend fun detailsFor(id: String): LibraryBookDetailsEntity?
    @Upsert suspend fun saveDetails(value: LibraryBookDetailsEntity)
    @Query("SELECT * FROM library_shelves ORDER BY nameKey, id") suspend fun shelves(): List<ShelfEntity>
    @Insert suspend fun insertShelf(value: ShelfEntity)
    @Query("UPDATE library_shelves SET name = :name, nameKey = :key WHERE id = :id") suspend fun renameShelf(id: String, name: String, key: String): Int
    @Query("DELETE FROM library_shelves WHERE id = :id") suspend fun deleteShelf(id: String)
    @Query("SELECT * FROM library_memberships") suspend fun memberships(): List<ShelfMembershipEntity>
    @Query("SELECT shelfId FROM library_memberships WHERE bookId = :id") suspend fun membershipsFor(id: String): List<String>
    @Query("DELETE FROM library_memberships WHERE bookId = :id") suspend fun clearMemberships(id: String)
    @Insert suspend fun insertMembership(value: ShelfMembershipEntity)
    @Query("DELETE FROM library_memberships WHERE bookId = :id AND shelfId = :shelf") suspend fun removeMembership(id: String, shelf: String)
    @Query("SELECT * FROM library_preferences WHERE id = 'global'") suspend fun preferences(): LibraryPreferencesEntity?
    @Upsert suspend fun savePreferences(value: LibraryPreferencesEntity)
    @Query("UPDATE library_preferences SET shelfId = NULL WHERE shelfId = :id") suspend fun clearShelfFilter(id: String)
    @Query("SELECT p.bookId, p.percent FROM reading_progress p INNER JOIN books b ON b.id = p.bookId AND b.currentRevision = p.contentRevision WHERE b.availability != 'DELETING'")
    suspend fun progress(): List<LibraryProgressRow>

    /** Deliberately leaves identities, source copies, revisions and writer epochs untouched. */
    @Query("UPDATE books SET title = :title, author = :author, coverKey = :coverKey, updatedAt = :now WHERE id = :id AND availability != 'DELETING'")
    suspend fun updateMetadata(id: String, title: String, author: String?, coverKey: String?, now: Long): Int
}
