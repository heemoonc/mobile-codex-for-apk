package com.example.workflow.notes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OperationStatus(val busy:Boolean=false,val completed:Boolean=false,val operationError:String?=null,val observationError:String?=null) {
 val error:String? get()=listOfNotNull(operationError,observationError).distinct().joinToString("\n").takeIf{it.isNotEmpty()}
}
fun editorCanExit(busy:Boolean,completed:Boolean)=!busy && !completed
class OperationController {
 private val mutable=MutableStateFlow(OperationStatus())
 val state=mutable.asStateFlow()
 fun begin():Boolean {
  while(true) {
   val current=mutable.value
   if(current.busy||current.completed)return false
   if(mutable.compareAndSet(current,current.copy(busy=true,operationError=null)))return true
  }
 }
 fun observationFailed(message:String) {mutable.update{it.copy(observationError=message)}}
 fun showError(message:String) {mutable.update{it.copy(operationError=message)}}
 fun finish(error:String?=null) {mutable.update{it.copy(busy=false,operationError=error)}}
 fun finishSaved() {mutable.update{it.copy(busy=false,completed=true,operationError=null)}}
 fun consumeSaved() {mutable.update{it.copy(completed=false)}}
}
