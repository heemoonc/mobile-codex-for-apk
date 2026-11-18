package com.example.workflow.storageprobe

import java.io.File
import java.util.UUID
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

data class ProbeResult(val name: String, val passed: Boolean, val detail: String = "")
object DataStoreVerifier {
    suspend fun run(parent: File): List<ProbeResult> {
        val dir = File(parent, "datastore-" + UUID.randomUUID())
        check(dir.mkdir())
        val file = File(dir, "probe.preferences_pb")
        val jobs = mutableListOf<Job>()
        val results = mutableListOf<ProbeResult>()
        suspend fun verify(name: String, block: suspend () -> Unit) {
            try { withTimeout(10000) { block() }; results += ProbeResult(name, true) }
            catch (e: TimeoutCancellationException) { results += ProbeResult(name, false, "guard timeout") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { results += ProbeResult(name, false, "${e.javaClass.simpleName}: ${e.message}") }
        }
        fun store(): androidx.datastore.core.DataStore<Preferences> {
            val job = SupervisorJob(); jobs += job
            return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        }
        val message = stringPreferencesKey("message")
        val count = intPreferencesKey("count")
        val flag = booleanPreferencesKey("flag")
        try {
            var data = store()
            verify("datastore-write-read") {
                data.edit { it[message] = "한글 · 저장"; it[count] = 0; it[flag] = true }
                check(data.data.first()[message] == "한글 · 저장" && file.length() > 0)
            }
            verify("datastore-reopen") {
                jobs.last().cancelAndJoin()
                data = store()
                val saved = data.data.first()
                check(saved[message] == "한글 · 저장" && saved[count] == 0 && saved[flag] == true)
            }
            verify("datastore-atomic-updates") {
                coroutineScope { repeat(20) { launch { data.edit { prefs -> prefs[count] = (prefs[count] ?: 0) + 1 } } } }
                check(data.data.first()[count] == 20)
            }
            verify("datastore-types") {
                val saved = data.data.first()
                check(saved[message] == "한글 · 저장" && saved[flag] == true && saved[count] == 20)
            }
        } finally {
            withContext(NonCancellable) {
                jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
                check(dir.deleteRecursively()) { "owned DataStore cleanup failed" }
            }
        }
        return results
    }
}
