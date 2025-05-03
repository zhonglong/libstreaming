package com.realtek.rtkcastsender.encodecontrol;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

public class EGLEncoder implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "EGLEncoder";
    private RtkTextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLContext mEGLContextEncoder = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEGLSurfaceEncoder = EGL14.EGL_NO_SURFACE;

    private Surface decodeSurface;

    private int mWidth;
    private int mHeight;
    private int fps;
    private int mVideoInterval;
    private boolean mFrameAvailable = true;
    private onFrameCallBack mFrameCallBack;

    private static final int INITIAL_FRAMES_INTERVAL_MILLISECONDS = 20;
    private static final int INITIAL_FRAMES_NUM = 2;

    private static final long INITIAL_FRAMES_BASE_NANOSECONDS = INITIAL_FRAMES_INTERVAL_MILLISECONDS * 1000 * 1000 * INITIAL_FRAMES_NUM;

    private volatile boolean start;
    private int mInCount = 0;
    private int mOutCount = 0;

    private Object lock;

    public final static int MSG_NEXT_OUTPUT = 1;

    HandlerThread mHandleThread;
    Handler mHandle;

    public void setCallBack(onFrameCallBack callBack) {
        mFrameCallBack = callBack;
    }

    public interface onFrameCallBack {
        void onUpdate();
    }


    public EGLEncoder(Surface surface, int mWidth, int mHeight, int fps) {
        this.mWidth = mWidth;
        this.mHeight = mHeight;

        lock = new Object();
        initFPs(fps);
        eglSetup(surface);
        makeCurrent(0);
        setup();
    }

    private void initFPs(int fps) {
        this.fps = fps;
        mVideoInterval = 1000 / fps;
        Log.d(TAG, "initFPS, mVideoInterval:" + mVideoInterval);
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    private void eglSetup(Surface surface) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        EGLConfig configEncoder = getConfig(2);

        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        mEGLContextEncoder = EGL14.eglCreateContext(mEGLDisplay, configEncoder, mEGLContext,
                attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContextEncoder == null) {
            throw new RuntimeException("null context2");
        }

        // Create a pbuffer surface.
        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, mWidth,
                EGL14.EGL_HEIGHT, mHeight,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0);


        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }


        int[] surfaceAttribs2 = {
                EGL14.EGL_NONE
        };
        mEGLSurfaceEncoder = EGL14.eglCreateWindowSurface(mEGLDisplay, configEncoder, surface,
                surfaceAttribs2, 0);   //creates an EGL window surface and returns its handle
        checkEglError("eglCreateWindowSurface");
        if (mEGLSurfaceEncoder == null) {
            throw new RuntimeException("surface was null");
        }
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private void setup() {
        mTextureRender = new RtkTextureRender();
        mTextureRender.surfaceCreated();

        Log.d(TAG, "textureID=" + mTextureRender.getTextureId());
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        decodeSurface = new Surface(mSurfaceTexture);
    }

    public Surface getDecodeSurface() {
        return decodeSurface;
    }

    private EGLConfig getConfig(int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }

        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent(int index) {

        if (index == 0) {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        } else {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurfaceEncoder, mEGLSurfaceEncoder, mEGLContextEncoder)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        }

    }

    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurfaceEncoder, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }

    public void awaitNewImage() {
        synchronized (lock) {
            try {
                // wait 2 fps time. in normal case
                // it will be notified in on fps time
                lock.wait(mVideoInterval * 2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if(mFrameAvailable) {
                mSurfaceTexture.updateTexImage();
                mFrameAvailable = false;
            }
        }
    }

    public boolean swapBuffers() {
        boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurfaceEncoder);
        checkEglError("eglSwapBuffers");
        return result;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (lock) {

            ProfileTimeTracker.Trace(ProfileTimeTracker.EGL_ENCODE_IN_COUNT, mInCount, 0);

            mFrameAvailable = true;
            mInCount++;
        }
    }

    // nano seconds
    private long computePresentationTimeNsec(int frameIndex) {
        final long ONE_BILLION = 1000000000;
        if(frameIndex > INITIAL_FRAMES_NUM) {
            return (frameIndex - INITIAL_FRAMES_NUM) * ONE_BILLION / fps + INITIAL_FRAMES_BASE_NANOSECONDS;
        }
        else
            return frameIndex * INITIAL_FRAMES_INTERVAL_MILLISECONDS * 1000 * 1000;
    }

    public void drawImage() {
        mTextureRender.drawFrame(mSurfaceTexture);
    }

    private long getNextDelay(int drawIndex) {
        if(drawIndex <= INITIAL_FRAMES_NUM) {
                return INITIAL_FRAMES_INTERVAL_MILLISECONDS;
        } else {
            return mVideoInterval;
        }
    }
    /**
     * start screen recording
     */
    public void start() {
        new Thread(() -> run(), TAG).start();
    }

    private void run() {
        start = true;
        Log.i(TAG, "EGLEncoder started to run...");
        mOutCount = 0;
        mInCount = 0;
        mHandleThread = new HandlerThread("EGLHandler");
        mHandleThread.start();

        mHandle = new Handler(mHandleThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_NEXT_OUTPUT:
                        synchronized (lock) {
                            lock.notify();
                        }
                        sendMessageDelayed(Message.obtain(mHandle, MSG_NEXT_OUTPUT), getNextDelay(mOutCount));
                        break;
                }
            }
        };
        mHandle.sendMessageDelayed(Message.obtain(mHandle, MSG_NEXT_OUTPUT), getNextDelay(mOutCount));

        ProfileTimeTracker.Trace(ProfileTimeTracker.EGL_ENCODE_START_TIME, SystemClock.elapsedRealtime(), 0);

        while (start) {
            makeCurrent(1);

            // wait for new image, update to the latest texture.
            awaitNewImage();

            if (mOutCount <= INITIAL_FRAMES_NUM) {
                Log.i(TAG, "EGLEncoder draw first " + mOutCount + " frame");
            }

            ProfileTimeTracker.Trace(ProfileTimeTracker.EGL_ENCODE_OUT_COUNT, mOutCount, 0);
            ProfileTimeTracker.Trace(ProfileTimeTracker.EGL_ENCODE_CUR_UPDATE_TIME, SystemClock.elapsedRealtime(), 0);

            drawImage();
            //mFrameCallBack.onUpdate();
            setPresentationTime(computePresentationTimeNsec(mOutCount));
            swapBuffers();
            mOutCount++;

        }
        mHandleThread.quit();
        Log.i(TAG, "EGLEncoder stopped...");
    }

    public void stop() {
        start = false;
    }

    static class ProfileTimeTracker {
        public final static int EGL_ENCODE_START_TIME = 0;
        public final static int EGL_ENCODE_FIRST_TIME = 1;
        public final static int EGL_ENCODE_IN_COUNT = 2;
        public final static int EGL_ENCODE_OUT_COUNT = 3;
        public final static int EGL_ENCODE_LAST_UPDATE_TIME = 4;
        public final static int EGL_ENCODE_CUR_UPDATE_TIME = 5;

        public static void Trace(int tag, long val, int extra) { }
    }
}
