package net.majorkernelpanic.example1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionManager;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
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
public class MainActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String TAG = "MainActivity";
    private final static String KEY_RTSP = "rtsp";
    private final static String KEY_TUIO = "tuio";
    private final static String KEY_VIDEO = "video";
    private final static String KEY_AUDIO = "audio";
    private final static String KEY_TRANSPORT = "transport";
    private final static boolean MULTICAST_DEBUG = false;

    private Context mContext;
    private SharedPreferences mPref;
    private ListPreference mVideo, mAudio, mTransport;

    private final Session.Callback mCallback = new Session.Callback() {
        @Override
        public void onBitrateUpdate(long bitrate) {
            Log.d(TAG, "onBitrateUpdate -> " + bitrate/1000 + " KB/S");
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        mContext = getApplicationContext();

        // Sets the port of the RTSP server to 1234
        // ffplay -rtsp_transport udp_multicast -fflags nobuffer -flags low_delay -sync video -i rtsp://192.168.100.39:8554/live
        // vlc --network-caching=40 rtsp://192.168.100.39:8554/live
        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        mPref.registerOnSharedPreferenceChangeListener(this);
        Editor editor = mPref.edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(8554));
        editor.commit();

        boolean audio = !TextUtils.equals(mPref.getString(KEY_AUDIO, "0"), "-1");
        // Configures the SessionBuilder
        SessionBuilder.getInstance()
                .setContext(getApplicationContext())
                .setMediaProjection(getMediaProjection())
                .setCallback(mCallback)
                .setAudioEncoder(audio ? SessionBuilder.AUDIO_AAC : SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264);

        // Starts the RTSP server
        if (mPref.getBoolean(KEY_RTSP, false)) {
            mContext.startService(new Intent(mContext, RtspServer.class));
        }
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
    protected void onResume() {
        super.onResume();
        EncoderDebugger.dump("video/avc");
    }

    private MediaProjection getMediaProjection() {
        try {
            IBinder b = ServiceManager.getService(Context.MEDIA_PROJECTION_SERVICE);
            IMediaProjectionManager projectionManager = IMediaProjectionManager.Stub.asInterface(b);
            IMediaProjection projection = projectionManager.createProjection(mContext.getApplicationInfo().uid, mContext.getPackageName(), 0, false);
            return new MediaProjection(mContext, IMediaProjection.Stub.asInterface(projection.asBinder()));
        } catch (Exception e) {
            return null;
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        switch (s) {
            case KEY_RTSP:
                if (mPref.getBoolean(KEY_RTSP, false)) {
                    mContext.startService(new Intent(mContext, RtspServer.class));
                } else {
                    mContext.stopService(new Intent(mContext, RtspServer.class));
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
            default:
                VideoQuality vq = mPref.getBoolean("uhd", false) ? VideoQuality.UHD_VIDEO_QUALITY : VideoQuality.DEFAULT_VIDEO_QUALITY;
                final String quality = mPref.getString("quality", "");
                if (!TextUtils.isEmpty(quality)) vq = VideoQuality.parseQuality(quality);
                SessionBuilder.getInstance().setVideoQuality(vq);
                break;
        }
    }
}
