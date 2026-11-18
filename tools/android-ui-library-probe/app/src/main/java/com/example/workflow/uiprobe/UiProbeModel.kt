package com.example.workflow.uiprobe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateMapOf
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.decode.DataSource
import coil3.size.Size
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

class UiProbeModel(app: Application) : AndroidViewModel(app) {
 val identity = UUID.randomUUID().toString().take(8)
 var activityCreations = 0
 var returning = false
 val names = listOf("nav-home", "nav-detail-argument", "nav-back", "material-button", "material-switch", "coil-decode", "coil-memory-cache", "coil-invalid", "coil-async-image")
 private val ledger = ResultLedger(names.toSet())
 val results = mutableStateMapOf<String, Boolean>()
 val details = mutableStateMapOf<String, String>()
 val directory = File(app.cacheDir, "ui-probe-" + UUID.randomUUID()).apply { check(mkdirs()) }
 val image = File(directory, "sample.png")
 val loader = ImageLoader.Builder(app).build()
 init {
  val bitmap = Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888)
  try {
   bitmap.eraseColor(Color.rgb(16,110,190))
   for (x in 0 until 32) for (y in 0 until 32) bitmap.setPixel(x,y,Color.rgb(245,160,35))
   image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
  } finally { bitmap.recycle() }
  viewModelScope.launch {
   try {
    withTimeout(15000) {
     val request=ImageRequest.Builder(app).data(image).size(Size.ORIGINAL).memoryCacheKey("probe-"+identity).build()
     val first=loader.execute(request)
     record("coil-decode", first is SuccessResult && first.image.width==64 && first.image.height==32, first.toString())
     val second=loader.execute(request)
     record("coil-memory-cache", second is SuccessResult && second.dataSource==DataSource.MEMORY_CACHE, second.toString())
     val broken=File(directory,"broken.png").apply { writeBytes(byteArrayOf(1,2,3,4)) }
     val invalid=loader.execute(ImageRequest.Builder(app).data(broken).build())
     record("coil-invalid", invalid is ErrorResult, invalid.toString())
    }
   } catch (e: TimeoutCancellationException) {
    failPendingCoil("timeout")
   } catch (e: CancellationException) { throw e
   } catch(e: Exception) { failPendingCoil(e.javaClass.simpleName+": "+e.message) }
  }
 }
 fun record(name:String, passed:Boolean, detail:String="") {
  ledger.record(name,passed);results[name]=passed
  if (!passed) details[name]=detail.take(250) else details.remove(name)
 }
 private fun failPendingCoil(message:String) { listOf("coil-decode","coil-memory-cache","coil-invalid").filter { it !in results }.forEach { record(it,false,message) } }
 override fun onCleared() { try { loader.shutdown() } finally { directory.deleteRecursively() }; super.onCleared() }
}
