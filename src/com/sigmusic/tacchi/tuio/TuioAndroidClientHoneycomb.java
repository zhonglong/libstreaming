package com.sigmusic.tacchi.tuio;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import TUIO.TuioClient;
import TUIO.TuioContainer;
import TUIO.TuioCursor;
import TUIO.TuioListener;
import TUIO.TuioObject;
import TUIO.TuioTime;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

public class TuioAndroidClientHoneycomb implements TuioListener {
	public final static String TAG = "TuioAndroidClient";
	TuioClient client;
	TuioService callback;
	int width, height;
	int port;
	
	BlockingQueue<MotionEvent> events = new LinkedBlockingQueue<>();
	

	
	
	public TuioAndroidClientHoneycomb(TuioService callback,int port, int width, int height) {
		client = new TuioClient(port);
		client.addTuioListener(this);
		this.callback = callback;
		this.port = port;
		this.width = width;
		this.height = height;
	}
	
	private static boolean running = true;
	private class EventSender extends Thread {
		public void run() {
			while (running) {
				MotionEvent me = null;
				try {
					me = events.poll(500, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (me != null) {
					callback.sendMotionEvent(me);
					me.recycle();
				}
			}
		}
	}
	
	private void sendUpdateEvent(MotionEvent update) {
		// Deprecated
	}
	private void sendUpDownEvent(MotionEvent updownevent) {
		// Deprecated
	}
	
	EventSender eventsender;
	
	public void addListener(TuioListener listener) {
		client.addTuioListener(listener);
	}
	
	public void cancelAll() {
		//TODO make this cancel everything
	}
	
	public void start() {
		Log.v(TAG, "Starting TUIO client on port:" +port);
		client.connect();
		eventsender = new EventSender();
		running = true;
		eventsender.start();
	}
	
	public void stop() {
		Log.v(TAG, "Stopping client");
		client.disconnect();
		running = false;
	}
	
	
	
	private int getNumCursors() {
		return client.getTuioCursors().size() + client.getTuioObjects().size();
	}
	private MotionEvent makeOrUpdateMotionEvent(TuioContainer point, long id, int action) {
		final long uptimeOffset = TuioTime.getStartTime().getTotalMilliseconds();
		long startms = point.getStartTime().getTotalMilliseconds();
		long totalms = point.getTuioTime().getTotalMilliseconds();
		int totalcursors = getNumCursors();
		int pointerId = 0;
		MotionEvent currentEvent = null;
		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
			//totalcursors -= 1;
			if (totalcursors <= 1) { //no cursors is a special case
				//id = -1;
				totalcursors = 1;
				action = MotionEvent.ACTION_UP; //no more ACTION_POINTER_UP. need to be serious.
			}
		}
		int actionmasked = action;//|( 0x1 << (7+id) & 0xff00);
		
		Log.d("TuioEvent", "TuioContainer: "+point.toString()+" startms: "+startms+" totalms: "+totalms+" cursors: "+totalcursors+" id: "+id+ " action: "+action+" Action masked: "+actionmasked);
		
	
		
		int[] pointerIds = new int[totalcursors];
		int i =0;
		PointerCoords[] inData = new PointerCoords[totalcursors]; 
		for (TuioObject obj : client.getTuioObjects()) {
			//if (   ( 1==1 ||  (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_POINTER_UP))  || obj.getSessionID() != id) { //we need to get rid of the removed cursor.
				//pointerIds[i] = new PointerProperties();
				pointerIds[i] = obj.getSymbolID();
				
				PointerCoords coord = new PointerCoords();
				
				coord.x = obj.getScreenX(width);
				coord.y = obj.getScreenY(height);
				coord.pressure = 1;//pressure
				coord.size = 0.1f; //size
				inData[i] = coord;
				i++;
			//}
		}
		Vector<TuioCursor> tuioCursors = client.getTuioCursors();
		Collections.sort(tuioCursors, new Comparator<TuioCursor>() {
			public int compare(TuioCursor object1, TuioCursor object2) {
				if (object1.getCursorID() > object2.getCursorID()) {
					return 1;
				} else if (object1.getCursorID() <  object2.getCursorID()) {
					return -1;
				}
				return 0;
			}});
		for (TuioCursor obj : tuioCursors) {
			//if (  ( 1 == 1 ||   (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_POINTER_UP)) || obj.getSessionID() != id) { //we need to get rid of the removed cursor.
				//pointerIds[i] = new PointerProperties();
				pointerIds[i] = i;
				
				PointerCoords coord = new PointerCoords();
				
				coord.x = obj.getScreenX(width);
				coord.y = obj.getScreenY(height);
				coord.pressure = 1;//pressure
				coord.size = 0.1f; //size
				inData[i] = coord;
				i++;
			//}
			if (obj.getCursorID() == id) pointerId = i;
		}
		
		try {
			if (action == MotionEvent.ACTION_UP && totalcursors < 1) {
	//			currentEvent.setAction(actionmasked); //nope that didn't fix it
				totalcursors = 1;
				currentEvent = MotionEvent.obtain(startms + uptimeOffset, SystemClock.uptimeMillis(), actionmasked, totalcursors, pointerIds, inData, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
				events.offer(currentEvent);
			
			} else {
				if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
					actionmasked = action | (1 << (8 + pointerId));
				}
				currentEvent = MotionEvent.obtain(startms + uptimeOffset, SystemClock.uptimeMillis(), actionmasked, totalcursors, pointerIds, inData, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
				events.offer(currentEvent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		
		
		return currentEvent;
	}
	
	

	@Override
	public void addTuioCursor(TuioCursor tcur) {
		Log.d(TAG, "Cursor down: "+tcur.toString());
		addTuioThing(tcur, tcur.getSessionID());
	}

	@Override
	public void addTuioObject(TuioObject tobj) {
		Log.d(TAG, "Fiducial down: "+tobj.toString());
		addTuioThing(tobj, tobj.getSessionID());
	}
	
	
	private void addTuioThing(TuioContainer point, long id) {
//		Log.d(TAG, "forwarding");
		//int event = (id == id) ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN;
		int event = (getNumCursors() <= 1) ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN;
		MotionEvent me = makeOrUpdateMotionEvent(point, id, event);
//		callback.sendMotionEvent(me);
		this.sendUpDownEvent(me);
	}
	

	@Override
	public void updateTuioCursor(TuioCursor tcur) {
		Log.d(TAG, "Cursor moved: "+tcur.toString());
		updateTuioThing(tcur, tcur.getSessionID());
	}

	@Override
	public void updateTuioObject(TuioObject tobj) {
		Log.d(TAG, "fiducial moved: "+tobj.toString());
		updateTuioThing(tobj, tobj.getSessionID());
	}
	
	private void updateTuioThing(TuioContainer point, long id) {
		int event = MotionEvent.ACTION_MOVE;
		MotionEvent me = makeOrUpdateMotionEvent(point, id, event);
//		callback.sendMotionEvent(me);
		this.sendUpdateEvent(me);

	}

	@Override
	public void removeTuioCursor(TuioCursor tcur) {
		Log.d(TAG, "Cursor up: "+tcur.toString());
		removeTuioThing(tcur, tcur.getSessionID());
	}

	@Override
	public void removeTuioObject(TuioObject tobj) {
		Log.d(TAG, "Fiducal up: "+tobj.toString());
		removeTuioThing(tobj, tobj.getSessionID());
	}
	
	private void removeTuioThing(TuioContainer point, long id) {
//		Log.d(TAG, "forwarding");
		int event = (getNumCursors() <= 1) ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP;
		MotionEvent me = makeOrUpdateMotionEvent(point, id, event);
		//callback.sendMotionEvent(me);
		this.sendUpDownEvent(me);
	}


	@Override
	public void refresh(TuioTime ftime) {		
	}


}
