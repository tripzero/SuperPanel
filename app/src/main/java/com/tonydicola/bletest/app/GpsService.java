package com.tonydicola.bletest.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


;

public class GpsService extends Service implements LocationListener, TextToSpeech.OnInitListener,
        GpsStatus.Listener, BleListener {

    private boolean tracking;
    private double distance;
    private double oldLat;
    private double oldLon;

    private double currentCurrent;
    private double currentPressure;
    private double currentTemperature;

    private TextToSpeech tts;

    private LocationManager locationManager;
    private Location mLocation;

    public double getDistance() {
        return distance;
    }

    public boolean getTracking() {
        return tracking;
    }

    public void setTracking(boolean t)
    {
        System.out.println("trying to turn tracking to: " + String.valueOf(t));
        tracking = t;
        trackingChanged(tracking);
    }

    public GpsService() {

    }

    // UUIDs for UAT service and associated characteristics.
    public static String SuperPanel_UUID = "5FAAF494-D4C6-483E-B592-D1A6FFD436C9";
    private static String rxUUid = "5faaf495-d4c6-483e-b592-d1a6ffd436c9";
    private static String txUUid = "5faaf496-d4c6-483e-b592-d1a6ffd436c9";

    public static String GPS_LAT = "lat";
    public static String GPS_LON = "lon";
    public static String GPS_Alt = "alt";
    public static String GPS_ACC = "acy";
    public static String GPS_SPD = "spd";
    public static String GPS_BER = "ber";
    public static String GPS_SAT = "sat";
    public static String GPS_FIX = "fixt";
    // BTLE state
    Ble ble;

    private boolean mConnected;
    private String jsonBuffer = "";

    private void writeToBle(String value)
    {
        if(!mConnected)
            return;

        ble.sendMessage(value);
    }

    public static void parseJSon(String jsonStr, Location location) {
        JSONObject json = null;
        try {
            json = new JSONObject(jsonStr);

            if(json.has(GPS_LAT))
            {
                location.setLatitude( json.getInt(GPS_LAT) / 100000);
            }
            else if(json.has(GPS_LON))
            {
                location.setLongitude(json.getInt(GPS_LON) / 100000);
            }
            else if(json.has(GPS_Alt))
            {
                location.setAltitude(json.getInt(GPS_Alt));
            }
            else if(json.has(GPS_ACC))
            {
                location.setAccuracy((float)json.getDouble(GPS_ACC));
            }
            else if(json.has(GPS_BER))
            {
                location.setBearing((float)json.getDouble(GPS_BER));
            }
            else if(json.has(GPS_SPD))
            {
                location.setSpeed((float)json.getDouble(GPS_SPD));
            }
        }
        catch(JSONException exception)
        {

        }
    }

    private void writeLine(String s) {
        System.out.println(s);
    }

    @Override
    public void onGpsStatusChanged(int event) {

    }
    static final int MSG_SET_CONNECTION = 5;
    static final int MSG_SET_SIGNAL = 10;

    //receive msgs

    static final int MSG_SET_TRACKING = 7;
    static final int MSG_REGISTER_CLIENT = 8;
    static final int MSG_UNREGISTER_CLIENT = 9;

    final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onBleMessage(String data) {
        System.out.println("received from device: " + data);

        jsonBuffer += data;

        String jsonStr;

        boolean locationNeedsUpdate = false;

        while((jsonStr = getJson()) != "") {
            if(jsonStr.contains(GPS_LAT) ||
                    jsonStr.contains(GPS_LON) ||
                    jsonStr.contains(GPS_Alt) ||
                    jsonStr.contains(GPS_SAT) ||
                    jsonStr.contains(GPS_SPD) ||
                    jsonStr.contains(GPS_FIX) ||
                    jsonStr.contains(GPS_BER))
            {
                /// we need this data here
                parseJSon(jsonStr, mLocation);
                locationNeedsUpdate = true;
            }
            rawJSon(jsonStr);
        }

        if(locationNeedsUpdate == true)
        {
            onLocationChanged(mLocation);
        }
    }

    private String getJson()
    {
        System.out.println("buffer: " + jsonBuffer);

        int i = jsonBuffer.indexOf("{");

        if( i == -1)
        {
            return "";
        }

        if(i > 0)
        {
            // clip off previous message
            jsonBuffer = jsonBuffer.substring(i);
        }

        int endMessage = jsonBuffer.indexOf("}");

        if(endMessage == -1 || jsonBuffer.length() < endMessage + 1)
        {
            /// Partial message, return;
            System.out.println("partial message");
            return "";
        }

        String jsonStr = jsonBuffer.substring(0, endMessage+1);

        System.out.println("I think i have a complete message: " + jsonStr);

        if(jsonBuffer.length() > endMessage+1)
        {
            jsonBuffer = jsonBuffer.substring(endMessage+1);
            System.out.println("new buffer: " + jsonBuffer);
        }
        else
        {
            jsonBuffer = "";
        }

        return jsonStr;
    }

    @Override
    public void onBleConnect() {
        mConnected = true;
        connectionChanged();
    }

    @Override
    public void onBleDisconnect() {
        mConnected = false;
        connectionChanged();
    }

    class IncomingHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle = msg.getData();
            if(bundle != null)
            {
                String  cmd = bundle.getString("cmd");

                writeToBle(cmd);
            }

            switch (msg.what) {
                case MSG_SET_TRACKING:
                    setTracking(msg.arg1 == 1);
                    break;
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void connectionChanged() {
        boolean value = mConnected;
        System.out.println("sending signals change to clients " + String.valueOf(value));
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                // Send data as an Integer
                mClients.get(i).send(Message.obtain(null, MSG_SET_CONNECTION, value == true ? 1:0, 0));

            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    private void rawJSon(String value) {
        System.out.println("sending raw json to clients:" + value);
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                Bundle bundle = new Bundle();
                bundle.putString("json", value);
                Message message = new Message();
                message.setData(bundle);
                mClients.get(i).send(message);
            } catch (RemoteException e) {
                mClients.remove(i);
            }
        }
    }

    private void signalChanged(int value) {
        System.out.println("sending signals change to clients " + String.valueOf(value));
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                // Send data as an Integer
                mClients.get(i).send(Message.obtain(null, MSG_SET_SIGNAL, value, 0));

            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    private void trackingChanged(boolean value) {
        System.out.println("sending tracking change to clients " + String.valueOf(value));
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                // Send data as an Integer
                mClients.get(i).send(Message.obtain(null, MSG_SET_TRACKING, value ? 1:0, 0));

            } catch (RemoteException e) {
                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        writeLine("onStartCommand called");
        super.onStartCommand(intent,flags,startId);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mLocation = new Location("flp");

        try {
            locationManager.addTestProvider("flp", false, false, false, false, true,
                    true, true, 0, 3);

            locationManager.setTestProviderEnabled("flp", true);
        }
        catch(SecurityException exception)
        {
            System.out.println("mock location not enabled");
            System.out.println(exception.getMessage());
        }
        catch (IllegalArgumentException exception)
        {
            System.out.println("mock location not enabled");
            System.out.println(exception.getMessage());
        }

        Criteria criteria = new Criteria();

        tts = new TextToSpeech(this, this);

        ble = new Ble(getApplicationContext(), this, SuperPanel_UUID, rxUUid, txUUid);

        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {

        System.out.println("I'm going the distance: " + location.toString());

        try {
            locationManager.setTestProviderLocation("flp", mLocation);
        }
        catch(IllegalArgumentException exception)
        {
            System.out.println("incomplete location");
            System.out.println(exception.getMessage());
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        if(oldLat == 0) oldLat = lat;
        if(oldLon == 0) oldLon = lon;

        if(tracking) {

            double dist = calcDistance(lat, lon, oldLat, oldLon);

            System.out.println("dist: " + String.valueOf(dist));

            distance += dist;

            if(distance % 1609 < 19) {
                if (!tts.isSpeaking()) {
                    tts.speak("You have travelled " + String.valueOf(Math.ceil(distance / 1609)) + " miles", TextToSpeech.QUEUE_FLUSH, null);
                }
            }

            oldLat = lat;
            oldLon = lon;
        }
        else {
            oldLat = lat;
            oldLon = lon;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onDestroy() {
        writeLine("Cleaning up and being destroyed");
        locationManager.removeTestProvider("flp");
        if (tts!=null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private double calcDistance(double lat, double lng, double lat2, double lng2)
    ///Based on the Haversine equation from: http://www.gpsvisualizer.com/calculators.js
    {
        if (Math.abs((int)lat) > 90 || Math.abs((int)lng) > 180 || Math.abs((int)lat2) > 90 || Math.abs((int)lng2) > 180)
        {
            return 0;
        }

        double PI=3.141592;

        lat = lat * PI/180;
        lat2 = lat2 * PI/180;
        lng = lng * PI/180;
        lng2 = lng2 * PI/180;
        double deltalat = lat2 - lat; //delta
        double deltalng = lng2 - lng; //delta
        double avelat = (lat + lat2)/2; //average
        double EquatorialRadius = 6378137;
        double PolarRadius = 6356752;
        double r45 = EquatorialRadius * Math.sqrt((1 + ((PolarRadius * PolarRadius - EquatorialRadius * EquatorialRadius) / (EquatorialRadius * EquatorialRadius)) *
                (Math.sin(45) * Math.sin(45))));
        double a = ( Math.sin(deltalat / 2) * Math.sin(deltalat / 2) ) + (Math.cos(lat) * Math.cos(lat2) *
                Math.sin(deltalng / 2) * Math.sin(deltalng / 2) );
        double c = 2 * Math.atan(Math.sqrt(a) / Math.sqrt(1 - a));
        double d_ellipse = r45 * c;

        double dist = d_ellipse / 1000;

        return dist*1000; ///in m
    }

    @Override
    public void onInit(int code) {
        if (code == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            System.out.println("tts active");
        }
    }
}
