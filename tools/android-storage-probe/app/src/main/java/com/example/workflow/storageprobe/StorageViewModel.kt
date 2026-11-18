package com.example.workflow.storageprobe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProbeState(val run: Int = 0, val running: Boolean = false, val results: List<ProbeResult> = emptyList())
class StorageViewModel(application: Application) : AndroidViewModel(application) {
    val identity: String = UUID.randomUUID().toString().take(8)
    var activityCreations = 0
    private val mutable = MutableStateFlow(ProbeState())
    val state = mutable.asStateFlow()
    fun start() {
        if (mutable.value.running) return
        val run = mutable.value.run + 1
        mutable.value = ProbeState(run, true)
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val results = withContext(Dispatchers.IO) { DataStoreVerifier.run(app.cacheDir) + RoomVerifier.run(app) }
                mutable.value = ProbeState(run, false, results)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = ProbeState(run, false, listOf(ProbeResult("suite-execution", false, "${e.javaClass.simpleName}: ${e.message}"))) }
        }
    }
}
