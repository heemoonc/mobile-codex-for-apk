package com.example.workflow.workprobe
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProbeState(val run:Int=0,val running:Boolean=false,val results:List<ProbeResult> = emptyList())
@HiltViewModel
class WorkProbeModel @Inject constructor(@ApplicationContext private val context:Context,val repository:ProbeRepository):ViewModel() {
 val identity=UUID.randomUUID().toString().take(8)
 var activityCreations=0
 private val mutable=MutableStateFlow(ProbeState())
 val state=mutable.asStateFlow()
 fun start(activityRepository:ProbeRepository) {
  if(mutable.value.running)return
  val run=mutable.value.run+1;mutable.value=ProbeState(run,true)
  viewModelScope.launch {
   val first=listOf(ProbeResult("hilt-singleton",activityRepository===repository && (context as ProbeApplication).repository===repository),ProbeResult("hilt-qualifier",repository.prefix=="hilt-injected"))
   try {val results=withContext(Dispatchers.IO){WorkVerifier.run(context,repository)};mutable.value=ProbeState(run,false,first+results)}
   catch(e:CancellationException){throw e}
   catch(e:Exception){mutable.value=ProbeState(run,false,first+ProbeResult("suite-execution",false,e.javaClass.simpleName+": "+e.message))}
  }
 }
}
