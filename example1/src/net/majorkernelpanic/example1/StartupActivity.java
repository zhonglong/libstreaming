package net.majorkernelpanic.example1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.goke.videotest.utils.AverageTime;

import net.majorkernelpanic.streaming.StreamingTool;

public class StartupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StreamingTool.init(this);
//        AverageTime.DEBUG = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);
        doFrame();
    }

    @Override
    protected void onDestroy() {
        mTick = -1;
        mWm.removeView(mTv);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private volatile int mTick;
    private WindowManager mWm;
    private TextView mTv;
    private void doFrame() {
        mWm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.gravity = Gravity.END | Gravity.TOP;
        lp.format = PixelFormat.RGBA_8888;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;

        mTv = new TextView(this);
        mTv.setWidth(500);
        mTv.setHeight(400);
        mTv.setGravity(Gravity.CENTER);
        mTv.setBackgroundColor(Color.WHITE);
        mTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 300);
        mTv.setTextColor(Color.BLACK);
        mWm.addView(mTv, lp);

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mTick < 0) return;
                mTick = (mTick + 1) % 100;
                mTv.setText(mTick < 10 ? "0" + mTick : "" + mTick);
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

}
