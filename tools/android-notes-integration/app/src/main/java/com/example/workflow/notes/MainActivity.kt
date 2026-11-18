package com.example.workflow.notes
import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity:ComponentActivity() {
 private val model:NotesModel by viewModels()
 private val resilienceModel:ResilienceModel by viewModels()
 private val remoteModel:RemoteSyncModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   val state by model.state.collectAsStateWithLifecycle()
   val title by model.title.collectAsStateWithLifecycle()
   val body by model.body.collectAsStateWithLifecycle()
   val nav=rememberNavController()
   var deleteId by rememberSaveable{mutableStateOf<Long?>(null)}
   var screenMenu by remember{mutableStateOf(false)}
   MaterialTheme(colorScheme=if(state.dark)darkColorScheme() else lightColorScheme()) {
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.safeDrawingPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
       Text("통합 메모",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
       Row {
        Text("다크 모드");Switch(checked=state.dark,enabled=state.prefsReady&&!state.busy&&!state.completed,onCheckedChange=model::setDark)
        Box {
         TextButton(onClick={screenMenu=true}){Text("화면")}
         DropdownMenu(expanded=screenMenu,onDismissRequest={screenMenu=false}) {
          DropdownMenuItem(text={Text("세로 보기")},onClick={screenMenu=false;requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT})
          DropdownMenuItem(text={Text("가로 보기")},onClick={screenMenu=false;requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE})
          DropdownMenuItem(text={Text("자동 회전")},onClick={screenMenu=false;requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED})
         }
        }
       }
      }
      Text("이 기기에 저장되는 나만의 메모",style=MaterialTheme.typography.bodyMedium)
      state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
      state.notice?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
      if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
      NavHost(navController=nav,startDestination="list",modifier=Modifier.weight(1f)) {
       composable("list") {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          Button(enabled=!state.busy,onClick={nav.navigate("edit/0")}) {Text("새 메모")}
          OutlinedButton(enabled=!state.busy,onClick={nav.navigate("remote")}) {Text("샘플 API")}
         }
         Text("메모 ${state.notes.size}개")
         if(!state.loaded)Text("메모를 불러오는 중입니다.")
         else if(state.notes.isEmpty())Text("아직 메모가 없습니다. 첫 메모를 작성해 보십시오.")
         LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
          items(state.notes,key={it.id}) {note->
           Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
             Text(note.title,style=MaterialTheme.typography.titleLarge)
             Text(note.body,maxLines=3)
             Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
              OutlinedButton(enabled=!state.busy,onClick={nav.navigate("edit/${note.id}")}) {Text("수정")}
              TextButton(enabled=!state.busy,onClick={deleteId=note.id}) {Text("삭제")}
             }
            }
           }
          }
         }
        }
       }
       composable("remote"){RemoteScreen(remoteModel,resilienceModel){nav.popBackStack()}}
       composable("edit/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {entry->
        val id=entry.arguments?.getLong("id")?:0L
        BackHandler {if(model.canExitEditor())nav.popBackStack()}
        LaunchedEffect(id){model.load(id)}
        LaunchedEffect(state.completed){if(state.completed){if(!nav.popBackStack())nav.navigate("list"){popUpTo("list"){inclusive=true};launchSingleTop=true};model.consumeSaved()}}
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
         Text(if(id==0L)"새 메모 작성" else "메모 수정",style=MaterialTheme.typography.titleLarge)
         OutlinedTextField(value=title,onValueChange=model::changeTitle,label={Text("제목")},singleLine=true,enabled=state.editorReady&&!state.busy,modifier=Modifier.fillMaxWidth())
         OutlinedTextField(value=body,onValueChange=model::changeBody,label={Text("내용")},enabled=state.editorReady&&!state.busy,modifier=Modifier.fillMaxWidth().weight(1f),minLines=4)
         Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
          Button(enabled=state.editorReady&&!state.busy,onClick=model::save){Text("저장")}
          OutlinedButton(enabled=editorCanExit(state.busy,state.completed),onClick={if(model.canExitEditor())nav.popBackStack()}){Text("목록으로")}
         }
        }
       }
      }
      if(deleteId!=null) {
       AlertDialog(onDismissRequest={if(!state.busy)deleteId=null},title={Text("메모를 삭제하시겠습니까?")},text={Text("삭제한 메모는 되돌릴 수 없습니다.")},
        confirmButton={TextButton(enabled=!state.busy,onClick={val id=deleteId;deleteId=null;if(id!=null)model.delete(id)}){Text("삭제 확인")}},
        dismissButton={TextButton(onClick={deleteId=null}){Text("취소")}})
      }
     }
    }
   }
  }
 }
}
