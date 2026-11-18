package com.example.workflow.libraryprobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private class ScopeWorker : ViewModel() { val work = viewModelScope.launch { awaitCancellation() } }

data class ProbeState(val run: Int = 0, val running: Boolean = false, val results: List<ProbeResult> = emptyList())
class ProbeViewModel : ViewModel() {
    val identity: String = UUID.randomUUID().toString().take(8)
    var activityCreations = 0
    private val mutable = MutableStateFlow(ProbeState())
    val state = mutable.asStateFlow()
    var activeJob: Job? = null
        private set
    fun start() {
        if (mutable.value.running) return
        val run = mutable.value.run + 1
        mutable.value = ProbeState(run, true)
        activeJob = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { ProbeSuite.run() }.toMutableList()
                results += verifyViewModelStore()
                mutable.value = ProbeState(run, false, results)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = ProbeState(run, false, listOf(ProbeResult("suite-execution", false, e.javaClass.simpleName))) }
        }
    }
    private fun verifyViewModelStore(): ProbeResult {
        val store = ViewModelStore()
        val provider = ViewModelProvider.create(store, viewModelFactory { initializer { ScopeWorker() } })
        val worker = provider[ScopeWorker::class]
        return try {
            check(provider[ScopeWorker::class] === worker)
            store.clear()
            check(worker.work.isCancelled)
            ProbeResult("viewmodel-store-clear", true)
        } catch (e: Exception) { ProbeResult("viewmodel-store-clear", false, e.javaClass.simpleName) }
        finally { store.clear() }
    }
}
