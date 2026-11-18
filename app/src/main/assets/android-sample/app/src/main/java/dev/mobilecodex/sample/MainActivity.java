package dev.mobilecodex.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText(R.string.hello);
        text.setTextSize(24);
        text.setGravity(Gravity.CENTER);
        setContentView(text);
    }
}
