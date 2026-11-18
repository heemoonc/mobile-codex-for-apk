package com.example.workflow.notes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

data class NotesState(val notes:List<Note> = emptyList(),val loaded:Boolean=false,val dark:Boolean=false,val prefsReady:Boolean=false,val busy:Boolean=false,val error:String?=null,val notice:String?=null,val editorReady:Boolean=false,val completed:Boolean=false)
@HiltViewModel
class NotesModel @Inject constructor(private val repository:NotesRepository,private val saved:SavedStateHandle):ViewModel() {
 private val mutable=MutableStateFlow(NotesState())
 private val operation=OperationController()
 val state=combine(mutable,operation.state){ui,op->ui.copy(busy=op.busy,error=op.error,completed=op.completed)}.stateIn(viewModelScope,SharingStarted.Eagerly,NotesState())
 val title=saved.getStateFlow("title","")
 val body=saved.getStateFlow("body","")
 init {
  viewModelScope.launch {try{repository.notes.collect{notes->mutable.update{it.copy(notes=notes,loaded=true)}}}catch(e:CancellationException){throw e}catch(e:Exception){operation.observationFailed("목록을 읽지 못했습니다. "+e.message)}}
  viewModelScope.launch {try{repository.dark.collect{dark->mutable.update{it.copy(dark=dark,prefsReady=true)}}}catch(e:CancellationException){throw e}catch(e:Exception){operation.observationFailed("설정을 읽지 못했습니다. "+e.message)}}
 }
 private fun fail(message:String,e:Exception){operation.finish(message+" "+(e.message?:e.javaClass.simpleName))}
 fun canExitEditor()=editorCanExit(operation.state.value.busy,operation.state.value.completed)
 fun changeTitle(value:String){if(editorCanExit(operation.state.value.busy,operation.state.value.completed))saved["title"]=value}
 fun changeBody(value:String){if(editorCanExit(operation.state.value.busy,operation.state.value.completed))saved["body"]=value}
 fun load(id:Long) {
  if(operation.state.value.busy)return
  if(saved.get<Long>("draftId")==id){mutable.update{it.copy(editorReady=true)};return}
  if(!operation.begin())return
  mutable.update{it.copy(editorReady=false)}
  viewModelScope.launch {
   try {
    val note=if(id==0L)null else repository.find(id)?:error("메모를 찾을 수 없습니다.")
    saved["draftId"]=id;saved["title"]=note?.title?:"";saved["body"]=note?.body?:""
    mutable.update{it.copy(editorReady=true)};operation.finish()
   }catch(e:CancellationException){throw e}catch(e:Exception){fail("메모를 열지 못했습니다.",e)}
  }
 }
 fun save() {
  if(!mutable.value.editorReady || operation.state.value.busy || operation.state.value.completed)return
  val input=validateNote(title.value,body.value)
  if(input==null){operation.showError("제목은 1~120자, 내용은 10,000자 이내로 입력하십시오.");return}
  val id=saved.get<Long>("draftId")?:return
  if(!operation.begin())return
  viewModelScope.launch {
   try {
    repository.save(id,input)
    mutable.update{it.copy(notice=if(id==0L)"메모를 저장했습니다." else "메모를 수정했습니다.")};operation.finishSaved()
   }catch(e:CancellationException){throw e}catch(e:Exception){fail("저장하지 못했습니다. 입력은 유지됩니다.",e)}
  }
 }
 fun consumeSaved(){saved.remove<Long>("draftId");saved["title"]="";saved["body"]="";mutable.update{it.copy(editorReady=false)};operation.consumeSaved()}
 fun delete(id:Long) {
  if(!operation.begin())return
  viewModelScope.launch {try{repository.delete(id);mutable.update{it.copy(notice="메모를 삭제했습니다.")};operation.finish()}catch(e:CancellationException){throw e}catch(e:Exception){fail("삭제하지 못했습니다.",e)}}
 }
 fun setDark(value:Boolean) {
  if(!mutable.value.prefsReady || !operation.begin())return
  viewModelScope.launch {try{repository.setDark(value);operation.finish()}catch(e:CancellationException){throw e}catch(e:Exception){fail("설정을 저장하지 못했습니다.",e)}}
 }
}
