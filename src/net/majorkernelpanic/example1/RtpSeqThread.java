package net.majorkernelpanic.example1;

import android.util.Log;

import net.majorkernelpanic.streaming.SessionBuilder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class RtpSeqThread extends Thread {

    public static final String TAG = "H264Packetizer";

    private MulticastSocket mSocket;
    private int mSeq;

    public RtpSeqThread() {
        try {
            mSocket = new MulticastSocket(5006);
            mSocket.joinGroup(InetAddress.getByName("234.5.6.7"));
            Log.d(TAG, "" + mSocket.getReceiveBufferSize());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, 2048);
        while (true) {
            try {
                mSocket.receive(packet);
                int seq = parseSeq(packet.getData());
                if (seq - mSeq != 1) {
                    Log.d(TAG, "MISSED " + (seq - mSeq) + " before SEQ = " + seq);
                }
                mSeq = seq;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int parseSeq(byte[] buffer) {
        int result = 0;
        result |= (buffer[2] & 0xFF);
        result <<= 8;
        result |= (buffer[3] & 0xFF);
        return result;
    }
}
