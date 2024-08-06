package net.majorkernelpanic.example1;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.sigmusic.tacchi.tuio.TuioService;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

/**
 * A straightforward example of how to use the RTSP server included in libstreaming.
 */
public class MainActivity extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String TAG = "MainActivity";
    private final static String KEY_RTSP = "rtsp";
    private final static String KEY_TUIO = "tuio";
    private final static String KEY_VIDEO = "video";
    private final static String KEY_AUDIO = "audio";
    private final static String KEY_TRANSPORT = "transport";
    private final static String KEY_QUALITY = "quality";
    private final static boolean MULTICAST_DEBUG = false;

    private Context mContext;
    private SharedPreferences mPref;
    private ListPreference mVideo, mAudio, mTransport;

    private RtspServer mRtspServer;

    private final Session.Callback mCallback = new Session.Callback() {
        @Override
        public void onBitrateUpdate(long bitrate, long sentBytes) {
            Log.d(TAG, "onBitrateUpdate -> " + 8*bitrate/1000 + " kbps, sentBytes = " + (sentBytes>>10) + " KB");
            Log.d(TAG, "getTotalTxBytes -> " + (TrafficStats.getUidTxBytes(Process.myUid())>>10) + " KB");
        }

        @Override
        public void onSessionError(int reason, int streamType, Exception e) {
            Toast.makeText(mContext, "onSessionError -> " + e, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onPreviewStarted() {
            Log.d(TAG, "onPreviewStarted -> ");
        }

        @Override
        public void onSessionConfigured() {
            Log.d(TAG, "onSessionConfigured -> ");
        }

        @Override
        public void onSessionStarted() {
            Log.d(TAG, "onSessionStarted -> ");
        }

        @Override
        public void onSessionStopped() {
            Log.d(TAG, "onSessionStopped -> ");
        }
    };

    private ServiceConnection mRtspServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRtspServer = ((RtspServer.LocalBinder)service).getService();
            mRtspServer.setCallback(mCallback);
            if (mPref.getBoolean(KEY_RTSP, false) && !mRtspServer.isStarted()) {
                mRtspServer.start();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        mContext = getContext().getApplicationContext();

        // Sets the port of the RTSP server to 1234
        // ffplay -fs -rtsp_transport udp_multicast -fflags nobuffer -flags low_delay -sync video -i rtsp://192.168.100.39:8554/live
        // vlc --network-caching=40 rtsp://192.168.100.39:8554/live
        mPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        mPref.registerOnSharedPreferenceChangeListener(this);

        boolean audio = !TextUtils.equals(mPref.getString(KEY_AUDIO, "0"), "-1");
        String quality = mPref.getString(KEY_QUALITY, "");
        // Configures the SessionBuilder
        SessionBuilder.getInstance()
                .setAudioEncoder(audio ? SessionBuilder.AUDIO_AAC : SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(TextUtils.isEmpty(quality) ? VideoQuality.DEFAULT_VIDEO_QUALITY : VideoQuality.parseQuality(quality));

        // Starts the RTSP server
        mContext.startService(new Intent(mContext, RtspServer.class));
        getContext().bindService(new Intent(mContext, RtspServer.class), mRtspServiceConnection, Context.BIND_ABOVE_CLIENT);
        if (mPref.getBoolean(KEY_TUIO, false)) {
            mContext.startService(new Intent(mContext, TuioService.class));
        }
        mVideo = (ListPreference) findPreference(KEY_VIDEO);
        mVideo.setSummary(mVideo.getEntry());
        mAudio = (ListPreference) findPreference(KEY_AUDIO);
        mAudio.setSummary(mAudio.getEntry());
        mTransport = (ListPreference) findPreference(KEY_TRANSPORT);
        mTransport.setSummary(mTransport.getEntry());

        if (MULTICAST_DEBUG) {
            SessionBuilder.getInstance().setDestination("234.5.6.7");
            new RtpSeqThread().start();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        EncoderDebugger.dump("video/avc");
        EncoderDebugger.dump("video/hevc");
    }

    @Override
    public void onDestroy() {
        mPref.unregisterOnSharedPreferenceChangeListener(this);
        if (mRtspServer != null) mRtspServer.setCallback(null);
        getContext().unbindService(mRtspServiceConnection);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        switch (s) {
            case KEY_RTSP:
                if (mPref.getBoolean(KEY_RTSP, false)) {
                    if (mRtspServer != null) mRtspServer.start();
                } else {
                    if (mRtspServer != null) mRtspServer.stop();
                }
                break;
            case KEY_TUIO:
                if (mPref.getBoolean(KEY_TUIO, false)) {
                    mContext.startService(new Intent(mContext, TuioService.class));
                } else {
                    mContext.stopService(new Intent(mContext, TuioService.class));
                }
                break;
            case KEY_VIDEO:
                mVideo.setSummary(mVideo.getEntry());
                break;
            case KEY_AUDIO:
                mAudio.setSummary(mAudio.getEntry());
                final boolean audio = !TextUtils.equals(mPref.getString(KEY_AUDIO, "0"), "-1");
                SessionBuilder.getInstance().setAudioEncoder(audio ? SessionBuilder.AUDIO_AAC : SessionBuilder.AUDIO_NONE);
                break;
            case KEY_TRANSPORT:
                mTransport.setSummary(mTransport.getEntry());
                break;
            case KEY_QUALITY:
                final String quality = mPref.getString(KEY_QUALITY, "");
                if (!TextUtils.isEmpty(quality)) {
                    VideoQuality vq = VideoQuality.parseQuality(quality);
                    SessionBuilder.getInstance().setVideoQuality(vq);
                }
                break;
        }
    }
}
