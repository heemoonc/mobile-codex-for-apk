package com.example.workflow.storageprobe

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private class RollbackExpected : RuntimeException()
object RoomVerifier {
    suspend fun run(context: Context): List<ProbeResult> {
        val results = mutableListOf<ProbeResult>()
        val ownedNames = mutableListOf<String>()
        fun name(): String = ("library-probe-" + UUID.randomUUID() + ".db").also { ownedNames += it }
        fun open(n: String, migration: Boolean = true): ProbeDatabase {
            val builder = Room.databaseBuilder(context, ProbeDatabase::class.java, n)
            if (migration) builder.addMigrations(MIGRATION_1_2)
            return builder.build()
        }
        fun seedV1(n: String) {
            val file = context.getDatabasePath(n); check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
                raw.execSQL("CREATE TABLE notes (id INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(id))")
                raw.execSQL("INSERT INTO notes(id,text) VALUES(42,?)", arrayOf("기존 데이터"))
                raw.version = 1
            }
        }
        suspend fun verify(label: String, block: suspend () -> Unit) {
            try { withTimeout(15000) { block() }; results += ProbeResult(label, true) }
            catch (e: TimeoutCancellationException) { results += ProbeResult(label, false, "guard timeout") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { results += ProbeResult(label, false, "${e.javaClass.simpleName}: ${e.message}") }
        }
        val mainName = name()
        var db: ProbeDatabase? = null
        var primaryFailure: Throwable? = null
        try {
            db = open(mainName)
            verify("room-crud") {
                val dao = db!!.notes()
                dao.insert(Note(1, "한글 · 원본")); check(dao.find(1) == Note(1, "한글 · 원본"))
                dao.update(Note(1, "수정", 1)); check(dao.find(1) == Note(1, "수정", 1))
                dao.delete(Note(1, "수정", 1)); check(dao.all().isEmpty())
                dao.insert(Note(1, "Flow 원본"))
            }
            verify("room-flow") { coroutineScope {
                val dao = db!!.notes(); val first = CompletableDeferred<Unit>()
                val collect = async { dao.observe().distinctUntilChanged().onEach { if (it.size == 1) first.complete(Unit) }.take(2).toList() }
                withTimeout(5000) { first.await() }
                dao.insert(Note(2, "Flow 갱신"))
                val states = collect.await()
                check(states.first().map { it.id } == listOf(1) && states.last().map { it.id } == listOf(1, 2))
            } }
            verify("room-transaction-rollback") {
                val dao = db!!.notes(); val before = dao.all()
                var rolledBack = false
                try { db!!.withTransaction { dao.insert(Note(99, "롤백")); throw RollbackExpected() } }
                catch (e: RollbackExpected) { rolledBack = true }
                check(rolledBack && dao.find(99) == null && dao.all() == before)
            }
            verify("room-reopen") {
                db!!.notes().insert(Note(7, "재열기 보존", 1))
                db!!.close(); db = open(mainName)
                check(db!!.notes().find(7) == Note(7, "재열기 보존", 1))
            }
            verify("room-migration-1-2") {
                val n = name(); seedV1(n)
                val migrated = open(n)
                try {
                    check(migrated.notes().all() == listOf(Note(42, "기존 데이터", 0)))
                    check(migrated.openHelper.readableDatabase.version == 2)
                } finally { migrated.close() }
            }
            verify("room-missing-migration-denied") {
                val n = name(); seedV1(n)
                val blocked = open(n, false); var rejected = false
                try { blocked.notes().all() }
                catch (e: IllegalStateException) { rejected = e.message.orEmpty().contains("migration", ignoreCase = true) }
                finally { blocked.close() }
                check(rejected)
                SQLiteDatabase.openDatabase(context.getDatabasePath(n).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
                    check(raw.version == 1)
                    raw.rawQuery("SELECT id,text FROM notes", null).use { c -> check(c.count == 1 && c.moveToFirst() && c.getInt(0) == 42 && c.getString(1) == "기존 데이터") }
                }
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            val cleanup = mutableListOf<CleanupActions.Action>(CleanupActions.Action { db?.close(); Unit })
            ownedNames.forEach { n -> cleanup += CleanupActions.Action {
                val file = context.getDatabasePath(n)
                if (file.exists()) check(context.deleteDatabase(n)) { "owned Room cleanup failed: $n" }
            } }
            try { CleanupActions.runAll(*cleanup.toTypedArray()) }
            catch (cleanupFailure: Throwable) {
                val primary = primaryFailure
                if (primary == null) throw cleanupFailure
                if (primary !== cleanupFailure) primary.addSuppressed(cleanupFailure)
            }
        }
        return results
    }
}
