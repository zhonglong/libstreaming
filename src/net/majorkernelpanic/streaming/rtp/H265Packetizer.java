/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming.rtp;

import java.io.IOException;
import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.util.Log;

/**
 * 
 *   RFC 3984.
 *   
 *   H.264 streaming over RTP.
 *   
 *   Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *   
 */
public class H265Packetizer extends AbstractPacketizer implements Runnable {

	public final static String TAG = "H265Packetizer";
	public final static int H265_NALU_HEADER_SIZE = 2;

	private Thread t = null;
	private int naluLength = 0;
	private long delay = 0, oldtime = 0;
	private Statistics stats = new Statistics();
	private byte[] sps = null, pps = null, stapa = null;
	byte[] header = new byte[4+H265_NALU_HEADER_SIZE];
	private int count = 0;
	private int streamType = 1;


	public H265Packetizer() {
		super();
		socket.setClockFrequency(90000);
	}

	public void start() {
		if (t == null) {
			t = new Thread(this, TAG);
			t.start();
		}
	}

	public void stop() {
		if (t != null) {
			try {
				is.close();
			} catch (IOException e) {}
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {}
			t = null;
		}
	}

	public void run() {
		long duration = 0;
		Log.d(TAG,"H265 packetizer started !");
		stats.reset();
		count = 0;

		if (is instanceof MediaCodecInputStream) {
			streamType = 1;
			socket.setCacheSize(0);
		}

		try {
			while (!Thread.interrupted()) {

				oldtime = System.nanoTime();
				// We read a NAL units from the input stream and we send them
				send();
				// We measure how long it took to receive NAL units from the phone
				duration = System.nanoTime() - oldtime;

				stats.push(duration);
				// Computes the average duration of a NAL unit
				delay = stats.average();
				//Log.d(TAG,"duration: "+duration/1000000+" delay: "+delay/1000000);

			}
		} catch (IOException e) {
		} catch (InterruptedException e) {}

		Log.d(TAG,"H265 packetizer stopped !");

	}

	/**
	 * Reads a NAL unit in the FIFO and sends it.
	 * If it is too big, we split it in FU-A units (RFC 3984).
	 */
	@SuppressLint("NewApi")
	private void send() throws IOException, InterruptedException {
		int sum = H265_NALU_HEADER_SIZE, len = 0, type;

		if (streamType == 0) {
		} else if (streamType == 1) {
			// NAL units are preceeded with 0x00000001
			fill(header,0,header.length);
			ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
			//ts += delay;
			naluLength = is.available()+H265_NALU_HEADER_SIZE;
			if (!(header[0]==0 && header[1]==0 && header[2]==0)) {
				// Turns out, the NAL units are not preceeded with 0x00000001
				Log.e(TAG, "NAL units are not preceeded by 0x00000001");
				return;
			}
		} else {
		}

		// Parses the NAL unit type
		type = (header[4]&0x7E)>>1;

		//Log.d(TAG,"- Nal unit length: " + naluLength + " delay: "+delay/1000000+" type: "+type);

		// Small NAL unit => Single NAL unit 
		final int NALU = MAXPACKETSIZE-rtphl-3;
		if (naluLength<=NALU) {
			buffer = socket.requestBuffer();
			buffer[rtphl] = header[4];
			buffer[rtphl+1] = header[5];
			len = fill(buffer, rtphl+H265_NALU_HEADER_SIZE,  naluLength-H265_NALU_HEADER_SIZE);
			socket.updateTimestamp(ts);
			socket.markNextPacket();
			super.send(naluLength+rtphl);
			//Log.d(TAG,"----- Single NAL unit - len:"+len+" delay: "+delay);
		}
		// Large NAL unit => Split nal unit 
		else {

			// Set FU-A header
			header[2] = (byte) ((header[4]>>1)&0x3F);  // FU header type
			header[2] |= 0x80; // Start bit
			header[1] = header[5];
			// Set FU-A indicator
			header[0] = (byte) ((header[4] & 0x81) & 0xFF); // FU indicator NRI
			header[0] |= (49 << 1);

			while (sum < naluLength) {
				buffer = socket.requestBuffer();
				buffer[rtphl] = header[0];
				buffer[rtphl+1] = header[1];
				buffer[rtphl+2] = header[2];
				socket.updateTimestamp(ts);
				if ((len = fill(buffer, rtphl+3,  naluLength-sum > NALU ? NALU : naluLength-sum  ))<0) return; sum += len;
				// Last packet before next NAL
				if (sum >= naluLength) {
					// End bit on
					buffer[rtphl+2] |= 0x40;
					socket.markNextPacket();
				}
				super.send(len+rtphl+3);
				// Switch start bit
				header[2] = (byte) (header[2] & 0x7F);
				//Log.d(TAG,"----- FU-A unit, sum:"+sum);
			}
		}
	}

	private int fill(byte[] buffer, int offset,int length) throws IOException {
		int sum = 0, len;
		while (sum<length) {
			len = is.read(buffer, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream");
			}
			else sum+=len;
		}
		return sum;
	}

}
