package com.example.workflow.notes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

@HiltViewModel
class ResilienceModel @Inject constructor(@ApplicationContext private val context:Context):ViewModel() {
 private var activeJob:Job?=null
 fun cancel(){activeJob?.cancel()}
 private val running=MutableStateFlow(false)
 private val lines=MutableStateFlow<List<String>>(emptyList())
 val busy=running.asStateFlow()
 val results=lines.asStateFlow()
 fun run() {
  if(running.value)return
  running.value=true;lines.value=emptyList()
  activeJob=viewModelScope.launch(Dispatchers.IO) {
   try {
    val client=OkHttpClient.Builder().callTimeout(10,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    try {
     for((path,expected) in listOf("/get" to 200,"/bearer" to 401,"/status/403" to 403)) {
      ensureActive()
      val result=try {
       val response=probeHttpStatus(client,"https://httpbin.org"+path)
       val tls=response.tls
       if(response.code==expected&&tls!=null)"PASS · HTTPS $expected · $tls" else "FAIL · HTTPS 기대 $expected / 실제 ${response.code} · TLS $tls"
      }catch(e:Exception){ensureActive();"FAIL · HTTPS $expected · ${e.javaClass.simpleName}"}
      lines.value=lines.value+result
     }
    }finally{client.dispatcher.cancelAll();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
    ensureActive()
    lines.value=lines.value+runQuotaProbe(context)
   }catch(e:CancellationException){throw e}
   catch(e:Exception){lines.value=lines.value+("FAIL · 진단 중단 · "+e.javaClass.simpleName)}
  }.also { job ->
   job.invokeOnCompletion { cause ->
    if(cause is CancellationException)lines.value=lines.value+"진단 취소됨"
    running.value=false
   }
  }
 }
}

/** Only a unique temporary database is touched; never notes.db or shared storage. */
private fun runQuotaProbe(context:Context):String {
 val file=File.createTempFile("notes-quota-", ".db",context.cacheDir)
 return try {
  var full=false
  SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
   db.execSQL("CREATE TABLE seed (id INTEGER PRIMARY KEY, value TEXT, payload BLOB)")
   db.execSQL("INSERT INTO seed VALUES(1,'original',NULL)")
   db.setMaximumSize(128L*1024)
   db.beginTransaction()
   try {
    db.execSQL("UPDATE seed SET value='changed' WHERE id=1")
    db.execSQL("INSERT INTO seed VALUES(2,'oversized',zeroblob(262144))")
    db.setTransactionSuccessful()
   }catch(e:SQLiteFullException){full=true}
   finally {
    try{db.endTransaction()}catch(e:SQLiteException) {
     // SQLITE_FULL may auto-rollback natively before SQLiteSession ends its stack.
     // Accept only this exact already-ended condition, then verify original rows and reopen below.
     if(!full || e.message?.contains("no transaction is active")!=true)throw e
    }
   }
   check(full){"SQLite quota did not fail"}
   db.rawQuery("SELECT value FROM seed WHERE id=1",null).use {check(it.moveToFirst()&&it.getString(0)=="original")}
   db.rawQuery("SELECT COUNT(*) FROM seed",null).use {check(it.moveToFirst()&&it.getInt(0)==1)}
  }
  SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
   db.rawQuery("SELECT value FROM seed WHERE id=1",null).use {check(it.moveToFirst()&&it.getString(0)=="original")}
  }
  "PASS · 임시 DB 128KiB 한도 · SQLITE_FULL · 롤백·재열기 보존"
 }catch(e:Exception){"FAIL · 임시 DB 용량 검사 · ${e.javaClass.simpleName}: ${e.message}"}
 finally{check(SQLiteDatabase.deleteDatabase(file)){"Temporary DB cleanup failed"}}
}
