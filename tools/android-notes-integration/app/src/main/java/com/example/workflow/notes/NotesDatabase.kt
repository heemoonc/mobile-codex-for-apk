package com.example.workflow.notes
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
@Entity(tableName="notes")
data class Note(@PrimaryKey(autoGenerate=true) val id:Long=0,val title:String,val body:String,val updatedAt:Long)
@Dao interface NotesDao {
 @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC") fun observe():Flow<List<Note>>
 @Query("SELECT * FROM notes WHERE id=:id") suspend fun find(id:Long):Note?
 @Insert suspend fun insert(note:Note):Long
 @Query("UPDATE notes SET title=:title, body=:body, updatedAt=:updatedAt WHERE id=:id") suspend fun update(id:Long,title:String,body:String,updatedAt:Long):Int
 @Query("DELETE FROM notes WHERE id=:id") suspend fun delete(id:Long):Int
}
@Entity(tableName="remote_items")
data class RemoteItem(@PrimaryKey val id:Long,val title:String,val imagePath:String)
@Dao interface RemoteDao {
 @Query("SELECT * FROM remote_items ORDER BY id") fun observe():Flow<List<RemoteItem>>
 @Query("DELETE FROM remote_items") suspend fun clear()
 @Insert suspend fun insertAll(items:List<RemoteItem>)
}
val NOTES_MIGRATION_1_2=object:Migration(1,2) {
 override fun migrate(db:SupportSQLiteDatabase) {db.execSQL("CREATE TABLE IF NOT EXISTS remote_items (id INTEGER NOT NULL, title TEXT NOT NULL, imagePath TEXT NOT NULL, PRIMARY KEY(id))")}
}
@Entity(tableName="sync_history")
data class SyncRecord(@PrimaryKey val id:String,val state:String,val attempt:Int,val updatedAt:Long,val detail:String)
@Dao interface SyncHistoryDao {
 @Query("SELECT * FROM sync_history ORDER BY updatedAt DESC, id DESC") fun observe():Flow<List<SyncRecord>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(records:List<SyncRecord>)
 @Query("DELETE FROM sync_history WHERE id NOT IN (SELECT id FROM sync_history ORDER BY updatedAt DESC, id DESC LIMIT 30)") suspend fun prune()
}
val NOTES_MIGRATION_2_3=object:Migration(2,3) {
 override fun migrate(db:SupportSQLiteDatabase) {db.execSQL("CREATE TABLE IF NOT EXISTS sync_history (id TEXT NOT NULL, state TEXT NOT NULL, attempt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, detail TEXT NOT NULL, PRIMARY KEY(id))")}
}
@Database(entities=[Note::class,RemoteItem::class,SyncRecord::class],version=3,exportSchema=true)
abstract class NotesDatabase:RoomDatabase() { abstract fun notes():NotesDao; abstract fun remote():RemoteDao; abstract fun history():SyncHistoryDao }
