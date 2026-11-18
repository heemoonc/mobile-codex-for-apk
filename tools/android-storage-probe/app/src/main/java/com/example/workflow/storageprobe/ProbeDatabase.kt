package com.example.workflow.storageprobe

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(@PrimaryKey val id: Int, val text: String, @ColumnInfo(defaultValue = "0") val pinned: Int = 0)
@Dao interface NotesDao {
    @Insert suspend fun insert(note: Note)
    @Update suspend fun update(note: Note)
    @Delete suspend fun delete(note: Note)
    @Query("SELECT * FROM notes ORDER BY id") suspend fun all(): List<Note>
    @Query("SELECT * FROM notes ORDER BY id") fun observe(): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun find(id: Int): Note?
}
@Database(entities = [Note::class], version = 2, exportSchema = true)
abstract class ProbeDatabase : RoomDatabase() { abstract fun notes(): NotesDao }
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0") }
}
