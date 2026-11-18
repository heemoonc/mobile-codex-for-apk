package com.example.workflow.libraryprobe
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProbeViewModelTest {
 @Test fun storeClearCancelsRealViewModelScopeJob() = runTest {
  Dispatchers.setMain(StandardTestDispatcher(testScheduler))
  val store = ViewModelStore()
  try {
   val provider = ViewModelProvider.create(store, viewModelFactory { initializer { ProbeViewModel() } })
   val model = provider[ProbeViewModel::class]
   assertSame("same owner retains same ViewModel", model, provider[ProbeViewModel::class])
   model.start()
   assertNotNull("start must launch work in viewModelScope", model.activeJob)
   store.clear()
   assertTrue("clearing store must cancel owned work", model.activeJob!!.isCancelled)
  } finally { store.clear(); Dispatchers.resetMain() }
 }
}
