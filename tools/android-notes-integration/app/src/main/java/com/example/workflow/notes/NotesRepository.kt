package com.example.workflow.notes
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name="notes-settings")
@Singleton
class NotesRepository @Inject constructor(private val database:NotesDatabase,@param:ApplicationContext private val context:Context) {
 val notes=database.notes().observe()
 private val darkKey=booleanPreferencesKey("dark-mode")
 val dark=context.settingsStore.data.map{it[darkKey]?:false}
 suspend fun setDark(enabled:Boolean) {context.settingsStore.edit{it[darkKey]=enabled}}
 suspend fun find(id:Long)=database.notes().find(id)
 suspend fun save(id:Long,input:NoteInput):Long {
  val now=System.currentTimeMillis()
  if(id==0L)return database.notes().insert(Note(title=input.title,body=input.body,updatedAt=now))
  check(database.notes().update(id,input.title,input.body,now)==1){"수정할 메모를 찾을 수 없습니다."}
  return id
 }
 suspend fun delete(id:Long) {check(database.notes().delete(id)==1){"삭제할 메모를 찾을 수 없습니다."}}
}
