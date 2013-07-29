package com.zacharyliu.stepnavigation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class CompassHeading implements ICustomSensor {
	
	private final String TAG = "CompassHeading";
	private SensorManager mSensorManager;
	private Sensor accelerometer;
	private Sensor magnetometer;
	private CompassHeadingListener mListener;

	public CompassHeading(Context context, CompassHeadingListener listener) {
		mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		accelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_GRAVITY);
		magnetometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		mListener = listener;
	}
	
	public interface CompassHeadingListener {
		public void onHeadingUpdate(double heading);
	}
	
	private SensorEventListener mSensorEventListener = new SensorEventListener() {
		private float[] accelReadings;
		private float[] magnetReadings;
		private double azimuth;
		private boolean azimuthReady;
		private boolean accelReady = false;
		private boolean magnetReady = false;
		final private int AVERAGE_SIZE = 5;
		private double[] history = new double[AVERAGE_SIZE];
		private int historyIndex = 0;
		private float y;
		private float z;
		private int count = 0;

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}

		private double average() {
			double sum = 0.0;
			for (int i=0; i<history.length; i++) {
				sum += history[i];
			}
			return sum / history.length;
		}
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			float[] readings = event.values.clone();
			
			// Swap y and z axes to change azimuth to read based on screen facing direction
			y = readings[1];
			z = readings[2];
			// Need to make one axis negative to maintain right-hand system since only one axis swap made
			readings[2] = -y;
			readings[1] = z;
			
			switch (event.sensor.getType()) {
				case Sensor.TYPE_GRAVITY:
					accelReadings = readings;
					accelReady = true;
					break;
				case Sensor.TYPE_MAGNETIC_FIELD:
					magnetReadings = readings;
					magnetReady = true;
					break;
			}
			if (accelReady == true && magnetReady == true) {
				float[] R = new float[9];
				float[] I = new float[9];
				boolean success = SensorManager.getRotationMatrix(R, I, accelReadings, magnetReadings);
				if (success) {
					float[] values = new float[3];
					SensorManager.getOrientation(R, values);
					azimuth = Math.toDegrees(values[0]);
//					z = accelReadings[2];
//					if (z < 0) {
//						Log.v(TAG, "Flip");
//						azimuth += 180;
//					}
					if (azimuth > 360) {
						azimuth -= 360;
					} else if (azimuth < 0) {
						azimuth += 360;
					}
					if (!azimuthReady) azimuthReady = true;
					history[historyIndex] = azimuth;
					if (++historyIndex == AVERAGE_SIZE) historyIndex = 0;
					double average = average();
					mListener.onHeadingUpdate(azimuth);
					if (++count == AVERAGE_SIZE) {
						count = 0;
						Log.v(TAG, String.format("Compass: %.2f", azimuth));
					}
				}
				
				// Require a set of new values for each sensor
				accelReady = false;
				magnetReady = false;
			}
		}
	};
	
	public void resume() {
		mSensorManager.registerListener(mSensorEventListener, accelerometer,
				SensorManager.SENSOR_DELAY_UI);
		mSensorManager.registerListener(mSensorEventListener, magnetometer,
				SensorManager.SENSOR_DELAY_UI);
	}
	
	public void pause() {
		mSensorManager.unregisterListener(mSensorEventListener);
	}
}
