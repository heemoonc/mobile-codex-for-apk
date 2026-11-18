package com.example.workflow.uiprobe

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import coil3.compose.AsyncImage

class MainActivity : ComponentActivity() {
 private val model: UiProbeModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);model.activityCreations++
  setContent {
   MaterialTheme {
    var counter by rememberSaveable { mutableIntStateOf(0) }
    var enabled by rememberSaveable { mutableStateOf(false) }
    val nav=rememberNavController()
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
      Text("Navigation · Material3 · Coil 검증",style=MaterialTheme.typography.headlineSmall)
      Text("ViewModel ${model.identity} · Activity 생성 ${model.activityCreations}회")
      Text("저장 상태 카운터 $counter · 스위치 $enabled")
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Button(onClick={ requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }) { Text("세로 회전") }
       Button(onClick={ requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }) { Text("가로 회전") }
      }
      NavHost(navController=nav,startDestination="home",modifier=Modifier.fillMaxWidth().height(130.dp)) {
       composable("home") {
        LaunchedEffect(Unit) {
         model.record("nav-home",nav.currentDestination?.route=="home")
         if(model.returning) { model.record("nav-back",nav.currentDestination?.route=="home");model.returning=false }
        }
        Column {
         Text("현재 화면: 홈")
         Button(onClick={nav.navigate("detail/42")}) { Text("상세 화면 42") }
        }
       }
       composable("detail/{id}",arguments=listOf(navArgument("id") {type=NavType.IntType})) { entry ->
        val id=entry.arguments?.getInt("id")
        LaunchedEffect(entry) {model.record("nav-detail-argument",id==42)}
        Column {
         Text("현재 화면: 상세 · ID $id")
         Button(onClick={model.returning=true;if(!nav.popBackStack()){model.returning=false;model.record("nav-back",false,"popBackStack false")}}) {Text("홈으로 돌아가기")}
        }
       }
      }
      Button(onClick={counter++;model.record("material-button",counter>0)}) {Text("Material3 카운터 증가")}
      Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
       Text("Material3 스위치")
       Switch(checked=enabled,onCheckedChange={val changed=switchTransitionSucceeded(enabled,it);enabled=it;model.record("material-switch",changed)})
      }
      AsyncImage(model=model.image,imageLoader=model.loader,contentDescription="주황·파랑 진단 이미지",
       modifier=Modifier.size(128.dp,64.dp),
       onSuccess={model.record("coil-async-image",true)},
       onError={model.record("coil-async-image",false,it.result.throwable.toString())})
      Text("${model.results.values.count {it}} / ${model.names.size} 통과",style=MaterialTheme.typography.titleMedium)
      model.names.forEach {name ->
       val value=model.results[name]
       Text((when(value){true->"PASS";false->"FAIL";null->"PENDING"})+" · "+name+(model.details[name]?.let {" · $it"}?:""))
      }
      Text("로컬 PNG·앱 전용 캐시만 사용 · 네트워크 권한 없음")
      Text("버튼·스위치·상세 이동/복귀를 누른 뒤 회전 상태를 확인하십시오.")
     }
    }
   }
  }
 }
}
