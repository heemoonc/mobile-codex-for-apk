package dev.mobilecodex.sample
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContentView(TextView(this).apply { text = getString(R.string.hello); gravity = Gravity.CENTER; textSize = 24f })
 }
}
