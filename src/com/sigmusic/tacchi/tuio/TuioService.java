package com.sigmusic.tacchi.tuio;

import java.lang.reflect.Method;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

public class TuioService extends Service {
	public final static String TAG = "TuioService";
	public final static String PREFS_NAME = "tuioserviceprefs";

	
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        TuioService getService() {
            return TuioService.this;
        }
    }

    
    private TuioAndroidClientHoneycomb mClient;
    private Object windowman;
    private WindowManager window;
    
    private Method injectPointerEvent = getInjectPointerEvent();
    Method getInjectPointerEvent() {
    	try {
			return Class.forName("android.hardware.input.InputManager").getMethod("injectInputEvent", InputEvent.class, int.class);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
    }
    
    @Override
    public void onCreate() {
    	Log.d(TAG, "On create called");
    	
    	/** Open up the preferences and get the prefered port **/
    	SharedPreferences mPrefs = getSharedPreferences(PREFS_NAME, 0);
    	int port = intFromStr(mPrefs, "port", "3333");
    	
    	/** grab the window from the system service that we can grab the width and height from **/
    	window = (WindowManager) getSystemService(Context.WINDOW_SERVICE); 
        Display display = window.getDefaultDisplay();
    	int width = display.getWidth();
    	int height = display.getHeight();
    	Log.d(TAG, "Creating TUIO server with width: "+width+"and height:"+height);
    	
    	/** Start the TUIO client with the port from preferences and display parameters **/
        mClient = new TuioAndroidClientHoneycomb(this,port, width, height);
        try {
	        windowman =  Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null);
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }
    
    private int intFromStr(SharedPreferences prefs, String key, String defaultVal) {
    	return Integer.parseInt(prefs.getString(key, defaultVal));
    }
    
    public void sendMotionEvent(MotionEvent me) {
    	if (me != null) {
	        Log.i(TAG, "sending motion event -> " + me);
	        try {
	        	injectPointerEvent.invoke(windowman,me, 0);
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
    	}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        Log.i(TAG, "starting client");
        mClient.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	mClient.stop();
        // Tell the user we stopped.
        Toast.makeText(this, "Local service stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

}
