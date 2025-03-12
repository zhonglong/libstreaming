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
	private byte[] vps = null, sps = null, pps = null, stapa = null;
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

	public void setStreamParameters(byte[] pps, byte[] sps, byte[] vps) {
		this.pps = pps;
		this.sps = sps;
		this.vps = vps;

		// A STAP-A NAL (NAL type 48) containing the vps sps and pps of the stream
		// See: https://www.rfc-editor.org/rfc/rfc7798#section-4.4.2
		if (pps != null && sps != null && vps != null) {
			// STAP-A NAL header + NALU 1 (VPS) size + NALU 1 (SPS) size + NALU 2 (PPS) size = 8 bytes
			stapa = new byte[vps.length + sps.length + pps.length + 8];

			// STAP-A NAL header is 48
			stapa[0] = (48 << 1);
			stapa[1] = (byte) 0x01;

			// Write NALU 1 size into the array (NALU 1 is the VPS).
			stapa[2] = (byte) (vps.length >> 8);
			stapa[3] = (byte) (vps.length & 0xFF);

			// Write NALU 2 size into the array (NALU 2 is the SPS).
			stapa[vps.length + 4] = (byte) (sps.length >> 8);
			stapa[vps.length + 5] = (byte) (sps.length & 0xFF);

			// Write NALU 3 size into the array (NALU 3 is the PPS).
			stapa[vps.length +sps.length + 6] = (byte) (pps.length >> 8);
			stapa[vps.length +sps.length + 7] = (byte) (pps.length & 0xFF);

			// Write NALU 1 into the array, then write NALU 2 into the array, then write NALU 3 into the array.
			System.arraycopy(vps, 0, stapa, 4, vps.length);
			System.arraycopy(sps, 0, stapa, 6 + vps.length, sps.length);
			System.arraycopy(pps, 0, stapa, 8 + vps.length + sps.length, pps.length);
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


		// The stream already contains NAL unit type 32 or 33 or 34, we don't need
		// to add them to the stream ourselves
		if (type == 32 || type == 33 || type == 34) {
			Log.v(TAG,"VPS SPS or PPS present in the stream.");
			count++;
			if (count>4) {
				vps = null;
				sps = null;
				pps = null;
			}
		}

		// We send two packets containing NALU type 32 (VPS) 33 (SPS) and 34 (PPS)
		// Those should allow the H265 stream to be decoded even if no SDP was sent to the decoder.
		boolean keyFrame = (((MediaCodecInputStream)is).getLastBufferInfo().flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
		if ((type == 19 || keyFrame) && vps != null && sps != null && pps != null) {
			Log.v(TAG,"VPS SPS and PPS prepend to sync frame in the stream.");
			buffer = socket.requestBuffer();
			socket.markNextPacket();
			socket.updateTimestamp(ts);
			System.arraycopy(stapa, 0, buffer, rtphl, stapa.length);
			super.send(rtphl+stapa.length);
		}

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
