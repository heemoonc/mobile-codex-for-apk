package com.example.workflow.workprobe

import android.os.Bundle
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var repository: ProbeRepository
    private val model: WorkProbeModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.activityCreations++
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 80, 24, 48)
            fitsSystemWindows = true
        }
        val title = TextView(this).apply { text = "Android WorkManager·Hilt 검증"; textSize = 20f }
        val identity = TextView(this).apply { text = "ViewModel ${model.identity} · Activity 생성 ${model.activityCreations}회" }
        val retry = Button(this).apply { text = "검사 다시 실행"; setOnClickListener { model.start(repository) } }
        val portrait = Button(this).apply { text = "세로 회전 검사"; setOnClickListener { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT } }
        val landscape = Button(this).apply { text = "가로 회전 검사"; setOnClickListener { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE } }
        val output = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(title); root.addView(identity); root.addView(retry); root.addView(portrait); root.addView(landscape)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    retry.isEnabled = !state.running
                    val passed = state.results.count { it.passed }
                    output.text = "실행 ${state.run} · " + (if (state.running) "검사 중" else "${state.results.size}개 중 ${passed}개 통과") + "\n" +
                        state.results.joinToString("\n") { (if (it.passed) "PASS" else "FAIL") + " · " + it.name + (if (it.detail.isBlank()) "" else " · " + it.detail) } +
                        "\n\n전용 UUID 작업 검사 · 사용자 데이터 접근 없음\n기기 회전 후 ViewModel 번호·실행 횟수 유지 확인\n프로세스 종료·재부팅·Doze·네트워크 제약은 별도 범위"
                }
            }
        }
        if (model.state.value.run == 0) model.start(repository)
    }
}
