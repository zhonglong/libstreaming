package net.majorkernelpanic.example1;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.text.TextUtils;
import android.util.Log;

import net.majorkernelpanic.streaming.video.VideoQuality;

import java.util.Arrays;

public class EncoderDebugger {

    private static final String TAG = "H264";

    public static void dump(String mimeType) {
        final VideoQuality q = VideoQuality.DEFAULT_VIDEO_QUALITY;
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int j = 0; j < numCodecs; j++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
            if (!codecInfo.isEncoder()) continue;

            String[] types = codecInfo.getSupportedTypes();
            for (int i = 0; i < types.length; i++) {
                if (!TextUtils.equals(types[i], mimeType)) continue;

                MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
                for (int k = 0; k < capabilities.colorFormats.length; k++) {
                    if (MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface != capabilities.colorFormats[k]) continue;

                    Log.d(TAG, "" + codecInfo.getName());
                    Log.d(TAG, "isHardwareAccelerated -> " + codecInfo.isHardwareAccelerated());
                    Log.d(TAG, "getDefaultFormat -> " + capabilities.getDefaultFormat());
                    Log.d(TAG, "isBitrateModeSupported -> " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR));
                    Log.d(TAG, "getQualityRange -> " + capabilities.getEncoderCapabilities().getQualityRange());
                    Log.d(TAG, "getComplexityRange -> " + capabilities.getEncoderCapabilities().getComplexityRange());
                    Log.d(TAG, "getSupportedWidths -> " + capabilities.getVideoCapabilities().getSupportedWidths());
                    Log.d(TAG, "getSupportedHeights -> " + capabilities.getVideoCapabilities().getSupportedHeights());
                    Log.d(TAG, "getSupportedFrameRates -> " + capabilities.getVideoCapabilities().getSupportedFrameRates());
                    Log.d(TAG, "getBitrateRange -> " + capabilities.getVideoCapabilities().getBitrateRange());
                    for (MediaCodecInfo.CodecProfileLevel pl : capabilities.profileLevels) {
                        Log.d(TAG, "profileLevels -> " + pl.profile + " : " + pl.level);
                    }
                }
            }
        }
    }

}
