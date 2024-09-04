package net.majorkernelpanic.example1;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.DisplayInfo;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class PreviewFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "Preview";
    public final static String KEY_PREVIEW = "preview";

    private IBinder mDisplayBinder;
    private SurfaceView mSurfaceView;
    private Button mDisplayRtmp;

    private SharedPreferences mPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        mPref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mSurfaceView = view.findViewById(R.id.surface);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mDisplayRtmp.setVisibility(mPref.getBoolean(KEY_PREVIEW, false) ? View.GONE : View.VISIBLE);
                if (mPref.getBoolean(KEY_PREVIEW, false)) {
                    mDisplayBinder = createDisplay(holder.getSurface());
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mDisplayBinder != null) {
                    SurfaceControl.destroyDisplay(mDisplayBinder);
                    mDisplayBinder = null;
                }
            }
        });
        mDisplayRtmp = view.findViewById(R.id.b_start_stop);
        mDisplayRtmp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), DisplayRtmpActivity.class));
            }
        });
    }

    @Override
    public void onDestroy() {
        mPref.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (TextUtils.equals(s, KEY_PREVIEW)) {
            mDisplayRtmp.setVisibility(mPref.getBoolean(KEY_PREVIEW, false) ? View.GONE : View.VISIBLE);
            if (mPref.getBoolean(KEY_PREVIEW, false)) {
                mDisplayBinder = createDisplay(mSurfaceView.getHolder().getSurface());
            } else {
                if (mDisplayBinder != null) {
                    SurfaceControl.destroyDisplay(mDisplayBinder);
                    mDisplayBinder = null;
                }
            }
        }
    }

    private IBinder createDisplay(Surface surface) {
        boolean secure = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.R && "S" != Build.VERSION.CODENAME;
        IBinder displayBinder = SurfaceControl.createDisplay(TAG + "-display", secure);
        try {
            SurfaceControl.openTransaction();
            SurfaceControl.setDisplaySurface(displayBinder, surface);
            DisplayInfo d = DisplayManagerGlobal.getInstance().getDisplayInfo(0);
            SurfaceControl.setDisplayProjection(displayBinder, 0, new Rect(0, 0, d.logicalWidth, d.logicalHeight), new Rect(0, 0, mSurfaceView.getWidth(), mSurfaceView.getHeight()));
            SurfaceControl.setDisplayLayerStack(displayBinder, d.layerStack);
        } finally {
            SurfaceControl.closeTransaction();
        }
        return displayBinder;
    }

}
