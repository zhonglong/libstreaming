package net.majorkernelpanic.streaming;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

public class StreamingTool {

    private static final String KEY_CODEC = "codec";
    private static final boolean Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    public static void init(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (!TextUtils.isEmpty(pref.getString(KEY_CODEC, null))) return;
        String codec = findVideoCodec(MediaFormat.MIMETYPE_VIDEO_AVC);
        if (TextUtils.isEmpty(codec)) return;

        SharedPreferences.Editor editor = pref.edit();
        editor.putString(KEY_CODEC, codec);
        final int video, audio;
        switch (codec) {
            case "OMX.uapi.video.encoder.avc":
                video = 0;
                audio = 1997;
                break;
            case "c2.rk.avc.encoder":
                video = 1;
                audio = 0;
                editor.putBoolean("sleep", true);
                break;
            case "OMX.amlogic.video.encoder.avc":
                video = 2;
                audio = 8;
                break;
            default:
                video = 0;
                audio = Q ? 0 : -1;
                break;
        }
        editor.putString("video", String.valueOf(video));
        editor.putString("audio", String.valueOf(audio));
        editor.putString("quality", "4000-30-1920-1080");
        editor.apply();
    }

    private static String findVideoCodec(String mimeType) {
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int j = 0; j < numCodecs; j++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
            if (!codecInfo.isEncoder()) continue;

            String[] types = codecInfo.getSupportedTypes();
            for (int i = 0; i < types.length; i++) {
                if (!TextUtils.equals(types[i], mimeType)) continue;

                MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
                for (int k = 0; k < capabilities.colorFormats.length; k++) {
                    if (MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface != capabilities.colorFormats[k])
                        continue;
                    if (Q && codecInfo.isHardwareAccelerated()) return codecInfo.getName();
                }
            }
        }
        return null;
    }

}
