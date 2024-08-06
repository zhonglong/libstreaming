package net.majorkernelpanic.example1;

import android.app.Activity;
import android.os.Bundle;

import net.majorkernelpanic.streaming.StreamingTool;

public class StartupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StreamingTool.init(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

}
