package net.majorkernelpanic.streaming.rtsp;

import android.util.Log;

import com.goke.videotest.utils.AverageTime;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpReport implements Runnable{
    public static final String TAG = "H264Packetizer";
    public static final int MTU = 1500;

    @Override
    public void run() {
        byte[] buffer = new byte[MTU];
        DatagramPacket packet = new DatagramPacket(buffer, MTU);
        final AverageTime rtp = AverageTime.getInstance("rtp");

        try (DatagramSocket s = new DatagramSocket(RtspServer.DEFAULT_RTSP_PORT + 1)) {
            while (true) {
                s.receive(packet);

                byte[] data = packet.getData();
                int frameType = data[13] & 0x1F;
                if (frameType == 5 || frameType == 6) {
//                    Log.d(TAG, "rtp pack & transport spent(ms): " + rtp.averageUs() / 1000);
                }
                long timestamp = ((long) (data[4] & 0xFF) << 24) | ((long) (data[5] & 0xFF) << 16) | ((long) (data[6] & 0xFF) << 8) | ((long) data[7] & 0xFF);
                rtp.pop(timestamp);
//                Log.d("avoip", "+++++++++ " + timestamp);
            }
        } catch (Exception e) {
            Log.e(TAG, "receive -> ", e);
        }
    }
}
