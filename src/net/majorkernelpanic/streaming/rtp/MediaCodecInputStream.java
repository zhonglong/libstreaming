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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Base64;
import android.util.Log;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !  
 */
@SuppressLint("NewApi")
public class MediaCodecInputStream extends InputStream {

	public final String TAG = "MediaCodecInputStream"; 

	private MediaCodec mMediaCodec = null;
	private BufferInfo mBufferInfo = new BufferInfo();
	private ByteBuffer[] mBuffers = null;
	private ByteBuffer mBuffer = null;
	private int mIndex = -1;
	private boolean mClosed = false;
	
	public MediaFormat mMediaFormat;
	private BiConsumer<String, String> mCallback;
	private byte[] mSPS, mPPS;
	private boolean mFound;

	public MediaCodecInputStream(MediaCodec mediaCodec) {
		mMediaCodec = mediaCodec;
		mBuffers = mMediaCodec.getOutputBuffers();
	}

	public void setMediaFormatCallback(BiConsumer<String, String> callback) {
		mCallback = callback;
	}

	@Override
	public void close() {
		mClosed = true;
	}

	@Override
	public int read() throws IOException {
		return 0;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		int min = 0;

		try {
			if (mBuffer==null) {
				while (!Thread.interrupted() && !mClosed) {
					mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
					if (mIndex>=0 ){
						if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG && !mFound && mCallback != null) {
							byte[] csd = new byte[128];
							int len = mBufferInfo.size, p = 4, q = 4;
							mBuffers[mIndex].get(csd,0,len);
							if (len>0 && csd[0]==0 && csd[1]==0 && csd[2]==0 && csd[3]==1) {
								// Parses the SPS and PPS, they could be in two different packets and in a different order
								//depending on the phone so we don't make any assumption about that
								while (p<len) {
									while (!(csd[p+0]==0 && csd[p+1]==0 && csd[p+2]==0 && csd[p+3]==1) && p+3<len) p++;
									if (p+3>=len) p=len;
									if ((csd[q]&0x1F)==7) {
										mSPS = new byte[p-q];
										System.arraycopy(csd, q, mSPS, 0, p-q);
									} else {
										mPPS = new byte[p-q];
										System.arraycopy(csd, q, mPPS, 0, p-q);
									}
									p += 4;
									q = p;
								}
								mCallback.accept(Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP),
										Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP));
								mFound = true;
							}
						}

						//Log.d(TAG,"Index: "+mIndex+" Time: "+mBufferInfo.presentationTimeUs+" size: "+mBufferInfo.size);
						mBuffer = mBuffers[mIndex];
						mBuffer.position(0);
						break;
					} else if (mIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
						mBuffers = mMediaCodec.getOutputBuffers();
					} else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
						mMediaFormat = mMediaCodec.getOutputFormat();
						Log.i(TAG,mMediaFormat.toString());

						if (!mFound && mCallback != null) {
							MediaFormat format = mMediaFormat;
							ByteBuffer spsb = format.getByteBuffer("csd-0");
							ByteBuffer ppsb = format.getByteBuffer("csd-1");
							mSPS = new byte[spsb.capacity() - 4];
							spsb.position(4);
							spsb.get(mSPS, 0, mSPS.length);
							mPPS = new byte[ppsb.capacity() - 4];
							ppsb.position(4);
							ppsb.get(mPPS, 0, mPPS.length);
							mCallback.accept(Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP),
									Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP));
							mFound = true;
						}
					} else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
						Log.v(TAG,"No buffer available...");
						//return 0;
					} else {
						Log.e(TAG,"Message: "+mIndex);
						//return 0;
					}
				}			
			}
			
			if (mClosed) throw new IOException("This InputStream was closed");
			
			min = length < mBufferInfo.size - mBuffer.position() ? length : mBufferInfo.size - mBuffer.position(); 
			mBuffer.get(buffer, offset, min);
			if (mBuffer.position()>=mBufferInfo.size) {
				mMediaCodec.releaseOutputBuffer(mIndex, false);
				mBuffer = null;
			}
			
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		return min;
	}
	
	public int available() {
		if (mBuffer != null) 
			return mBufferInfo.size - mBuffer.position();
		else 
			return 0;
	}

	public BufferInfo getLastBufferInfo() {
		return mBufferInfo;
	}

}
