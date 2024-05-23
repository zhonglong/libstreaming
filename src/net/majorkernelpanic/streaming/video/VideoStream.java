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

package net.majorkernelpanic.streaming.video;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.exceptions.CameraInUseException;
import net.majorkernelpanic.streaming.exceptions.ConfNotSupportedException;
import net.majorkernelpanic.streaming.exceptions.InvalidSurfaceException;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.gl.EGLRender;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;

/** 
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream {

	protected final static String TAG = "VideoStream";

	protected VideoQuality mRequestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	protected VideoQuality mQuality = mRequestedQuality.clone(); 
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected Surface mSurface = null;
	protected SharedPreferences mSettings = null;
	protected int mVideoEncoder, mCameraId = 0;
	protected int mRequestedOrientation = 0, mOrientation = 0;
	protected Object mCamera;
	protected Thread mCameraThread;
	protected Looper mCameraLooper;

	protected boolean mCameraOpenedManually = true;
	protected boolean mFlashEnabled = false;
	protected boolean mSurfaceReady = false;
	protected boolean mUnlocked = false;
	protected boolean mPreviewStarted = false;
	protected boolean mUpdated = false;
	
	protected String mMimeType;
	protected String mEncoderName;
	protected int mEncoderColorFormat;
	protected int mCameraImageFormat;
	protected int mMaxFps = 0;	

	protected MediaProjection mMediaProjection;
	private EGLRender mGLRender;

	/** 
	 * Don't use this class directly.
	 * Uses CAMERA_FACING_BACK by default.
	 */
	public VideoStream() {
		this(CameraInfo.CAMERA_FACING_BACK);
	}	

	/** 
	 * Don't use this class directly
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	@SuppressLint("InlinedApi")
	public VideoStream(int camera) {
		super();
		setCamera(camera);
	}

	/**
	 * Sets the camera that will be used to capture video.
	 * You can call this method at any time and changes will take effect next time you start the stream.
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	public void setCamera(int camera) {
		// Deprecated
	}

	/**	Switch between the front facing and the back facing camera of the phone. 
	 * If {@link #startPreview()} has been called, the preview will be  briefly interrupted. 
	 * If {@link #start()} has been called, the stream will be  briefly interrupted.
	 * You should not call this method from the main thread if you are already streaming. 
	 * @throws IOException 
	 * @throws RuntimeException 
	 **/
	public void switchCamera() throws RuntimeException, IOException {
		// Deprecated
	}

	/**
	 * Returns the id of the camera currently selected. 
	 * Can be either {@link CameraInfo#CAMERA_FACING_BACK} or 
	 * {@link CameraInfo#CAMERA_FACING_FRONT}.
	 */
	public int getCamera() {
		return mCameraId;
	}

	/** Turns the LED on or off if phone has one. */
	public synchronized void setFlashState(boolean state) {
		// Deprecated
	}

	/** 
	 * Toggles the LED of the phone if it has one.
	 * You can get the current state of the flash with {@link VideoStream#getFlashState()}.
	 */
	public synchronized void toggleFlash() {
		setFlashState(!mFlashEnabled);
	}

	/** Indicates whether or not the flash of the phone is on. */
	public boolean getFlashState() {
		return mFlashEnabled;
	}

	/** 
	 * Sets the orientation of the preview.
	 * @param orientation The orientation of the preview
	 */
	public void setPreviewOrientation(int orientation) {
		mRequestedOrientation = orientation;
		mUpdated = false;
	}
	
	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality videoQuality) {
		if (!mRequestedQuality.equals(videoQuality)) {
			mRequestedQuality = videoQuality.clone();
			mUpdated = false;
		}
	}

	public void setMediaProjection(MediaProjection projection) {
		mMediaProjection = projection;
	}

	/** 
	 * Returns the quality of the stream.  
	 */
	public VideoQuality getVideoQuality() {
		return mRequestedQuality;
	}

	/**
	 * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called 
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	/**
	 * Configures the stream. You need to call this before calling {@link #getSessionDescription()} 
	 * to apply your configuration of the stream.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mOrientation = mRequestedOrientation;
	}	
	
	protected void reconfigure(String sps, String pps) {};

	/**
	 * Starts the stream.
	 * This will also open the camera and display the preview 
	 * if {@link #startPreview()} has not already been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		if (!mPreviewStarted) mCameraOpenedManually = false;
		super.start();
		Log.d(TAG,"Stream configuration: FPS: "+mQuality.framerate+" Width: "+mQuality.resX+" Height: "+mQuality.resY);
	}

	/** Stops the stream. */
	public synchronized void stop() {
		if (mCamera != null) {
			super.stop();
			// We need to restart the preview
			if (!mCameraOpenedManually) {
				destroyCamera();
			} else {
				try {
					startPreview();
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public synchronized void startPreview() 
			throws CameraInUseException, 
			InvalidSurfaceException, 
			RuntimeException {
		// Deprecated
	}

	/**
	 * Stops the preview.
	 */
	public synchronized void stopPreview() {
		// Deprecated
	}

	/**
	 * Video encoding is done by a MediaRecorder.
	 */
	protected void encodeWithMediaRecorder() throws IOException, ConfNotSupportedException {
		// Deprecated
	}


	/**
	 * Video encoding is done by a MediaCodec.
	 */
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {
		if (mMode == MODE_MEDIACODEC_API_2) {
			// Uses the method MediaCodec.createInputSurface to feed the encoder
			encodeWithMediaCodecMethod2();
		} else {
			// Uses dequeueInputBuffer to feed the encoder
			encodeWithMediaCodecMethod1();
		}
	}	

	/**
	 * Video encoding is done by a MediaCodec.
	 */
	@SuppressLint("NewApi")
	protected void encodeWithMediaCodecMethod1() throws RuntimeException, IOException {
		// Deprecated
	}

	/**
	 * Video encoding is done by a MediaCodec.
	 * But here we will use the buffer-to-surface method
	 */
	@SuppressLint({ "InlinedApi", "NewApi" })	
	protected void encodeWithMediaCodecMethod2() throws RuntimeException, IOException {

		Log.d(TAG,"Video encoded using the MediaCodec API with a surface");

		mMediaCodec = MediaCodec.createEncoderByType("video/avc");
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		mediaFormat.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
		if (mSettings != null && Integer.parseInt(mSettings.getString("transport", "2")) == TRANSPORT_TCP) {
			mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
			mediaFormat.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000);
		} else {
			mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
		}
		// TODO: RK3588
//		mediaFormat.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, mQuality.framerate);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mSurface = mMediaCodec.createInputSurface();
		mMediaCodec.start();

		// Updates the parameters of the camera if needed
		createCamera();

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		final MediaCodecInputStream inputStream = new MediaCodecInputStream(mMediaCodec);
		inputStream.setMediaFormatCallback((sps, pps) -> reconfigure(sps, pps));
		mPacketizer.setInputStream(inputStream);
		mPacketizer.start();

		mStreaming = true;

	}

	/**
	 * Returns a description of the stream using SDP. 
	 * This method can only be called after {@link Stream#configure()}.
	 * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
	 */	
	public abstract String getSessionDescription() throws IllegalStateException;

	protected synchronized void createCamera() throws RuntimeException {
		if (mCamera == null) {
			int videoSource = mSettings != null ? Integer.parseInt(mSettings.getString("video", "0")) : 0;
			switch (videoSource) {
				case 0:
					VirtualDisplay virtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display",
							mQuality.resX, mQuality.resY, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
							mSurface, null, null);
					mCamera = virtualDisplay;
					break;
				case 1:
				case 2:
					boolean egl = (videoSource == 2);
					if (egl) {
						mGLRender = new EGLRender(mSurface, mQuality.resX, mQuality.resY, mQuality.framerate);
					}

					boolean secure = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.R && "S" != Build.VERSION.CODENAME;
					IBinder displayBinder = SurfaceControl.createDisplay(TAG + "-display", secure);
					try {
						SurfaceControl.openTransaction();
						SurfaceControl.setDisplaySurface(displayBinder, egl ? mGLRender.getDecodeSurface() : mSurface);
						DisplayInfo d = DisplayManagerGlobal.getInstance().getDisplayInfo(0);
						SurfaceControl.setDisplayProjection(displayBinder, 0, new Rect(0, 0, d.logicalWidth, d.logicalHeight), new Rect(0, 0, mQuality.resX, mQuality.resY));
						SurfaceControl.setDisplayLayerStack(displayBinder, d.layerStack);
					} finally {
						SurfaceControl.closeTransaction();
					}
					mCamera = displayBinder;

					if (egl) {
						mGLRender.start();
					}
					break;
			}
		}
	}

	protected synchronized void destroyCamera() {
		if (mCamera != null) {
			if (mCamera instanceof VirtualDisplay) {
				VirtualDisplay virtualDisplay = (VirtualDisplay) mCamera;
				virtualDisplay.release();
			}
			if (mCamera instanceof IBinder) {
				if (mGLRender != null) {
					mGLRender.stop();
					mGLRender = null;
				}
				IBinder displayBinder = (IBinder) mCamera;
				SurfaceControl.destroyDisplay(displayBinder);
			}
			mCamera = null;
		}
	}

	// See: https://github.com/JumpingYang001/webrtc/blob/master/sdk/android/src/java/org/webrtc/HardwareVideoEncoder.java
	public void requestKeyFrame() {
		// Ideally MediaCodec would honor BUFFER_FLAG_SYNC_FRAME so we could
		// indicate this in queueInputBuffer() below and guarantee _this_ frame
		// be encoded as a key frame, but sadly that flag is ignored.  Instead,
		// we request a key frame "soon".
		try {
			Bundle b = new Bundle();
			b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
			mMediaCodec.setParameters(b);
		} catch (IllegalStateException e) {
			Log.e(TAG, "requestKeyFrame failed", e);
			return;
		}
	}
}
