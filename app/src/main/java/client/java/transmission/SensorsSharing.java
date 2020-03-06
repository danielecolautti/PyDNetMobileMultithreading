package client.java.transmission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.SENSOR_SERVICE;

public class SensorsSharing implements SensorEventListener, LocationListener {

    private final String TAG = "SensorsSharing";
    private Connection connection;
    private Context context;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor accelerometer,gyroscope,magneticField;

    private final int distanzaMinimaMetri = 0;
    private final int intervalFrequency = 2 * 1000;
    private long lastUpdateAcc = 0, lastUpdateGyr = 0, lastUpdateMag = 0;

    public SensorsSharing (String ip, int port, Context c) {
        connection = new Connection(ip, port);
        context = c;
    }

    public boolean create() {
        sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);

        Log.d(TAG,"Sensori disponibili:");
        for (int i=0; i<sensorManager.getSensorList(Sensor.TYPE_ALL).size() ; i++)
            Log.d(TAG,sensorManager.getSensorList(Sensor.TYPE_ALL).get(i).toString());

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope != null)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);

        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null)
            sensorManager.registerListener(this,magneticField,SensorManager.SENSOR_DELAY_NORMAL);

        locationManager = (LocationManager)context.getSystemService(LOCATION_SERVICE);
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalFrequency, distanzaMinimaMetri, this);

        return connection.startConnection() == 0;
    }

    public void destroy() {
        if (sensorManager!=null)
            sensorManager.unregisterListener(this);
        accelerometer = null;
        gyroscope = null;
        magneticField = null;
        if (locationManager!=null)
            locationManager.removeUpdates(this);
        locationManager = null;
        if (connection != null)
            connection.stopConnection();
    }

    @Override
    // ACCELEROMETRO + GIROSCOPIO + MAGNETOSCOPIO
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER
                && System.currentTimeMillis() - lastUpdateAcc > intervalFrequency) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            connection.invia("Accelerometro\t" + x + "\t" + y + "\t" + z);
            lastUpdateAcc = System.currentTimeMillis();
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE
                && System.currentTimeMillis() - lastUpdateGyr > intervalFrequency) {
            connection.invia("Giroscopio");
            lastUpdateGyr = System.currentTimeMillis();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD
                && System.currentTimeMillis() - lastUpdateMag > intervalFrequency) {
            connection.invia("Magnetoscopio");
            lastUpdateMag = System.currentTimeMillis();
        }
    }

    @Override
    // ACCELEROMETRO + GIROSCOPIO + MAGNETOSCOPIO
    public void onAccuracyChanged(Sensor s, int i) {
    }

    @Override
    // GPS
    public void onLocationChanged(Location location) {
        double latitude=location.getLatitude();
        double longitude=location.getLongitude();
        connection.invia("GPS\t" + latitude + "\t" + longitude);
    }

    @Override
    // GPS
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    // GPS
    public void onProviderEnabled(String s) {
    }

    @Override
    // GPS
    public void onProviderDisabled(String s) {
    }

}
