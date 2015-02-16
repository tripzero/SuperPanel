package com.tonydicola.bletest.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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

public class GpsService extends Service implements LocationListener, TextToSpeech.OnInitListener,
        GpsStatus.Listener {

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
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RBL_UART_UUID = UUID.fromString("713d0000-503e-4c75-ba94-3148f18d941e");
    public static UUID RBL_RX_UUID = UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");
    public static UUID RBL_TX_UUID = UUID.fromString("713d0003-503e-4c75-ba94-3148f18d941e");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static String GPS_LAT = "lat";
    public static String GPS_LON = "lon";
    public static String GPS_Alt = "alt";
    public static String GPS_ACC = "acy";
    public static String GPS_SPD = "spd";
    public static String GPS_BER = "ber";
    public static String GPS_SAT = "sat";
    public static String GPS_FIX = "fixt";
    // BTLE state
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    private boolean mConnected;
    private String jsonBuffer = "";

    private void writeToBle(String value)
    {
        if(tx == null || gatt == null || !mConnected)
            return;

        tx.setValue(value);
        gatt.writeCharacteristic(tx);
    }

    // Main BTLE device callback where much of the logic occurs.
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        // Called whenever the device connection state changes, i.e. from disconnected to connected.
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                writeLine("Connected!");
                mConnected = true;
                connectionChanged();

                // Discover services.
                if (!gatt.discoverServices()) {
                    writeLine("Failed to start discovering services!");
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                writeLine("Disconnected!");
                mConnected = false;
                connectionChanged();
                gatt.connect();
            } else {
                writeLine("Connection state changed.  New state: " + newState);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt,rssi,status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeLine(String.format("BluetoothGatt ReadRssi[%d]", rssi));
                signalChanged(rssi);
            }
        }

        // Called when services have been discovered on the remote device.
        // It seems to be necessary to wait for this discovery to occur before
        // manipulating any services or characteristics.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeLine("Service discovery completed!");
            }
            else {
                writeLine("Service discovery failed with status: " + status);
            }
            // Save reference to each characteristic

            tx = gatt.getService(RBL_UART_UUID).getCharacteristic(TX_UUID);
            rx = gatt.getService(RBL_UART_UUID).getCharacteristic(RX_UUID);

            if(tx == null)
            {
                gatt.getService(RBL_UART_UUID).getCharacteristic(RBL_TX_UUID);
            }

            if(rx == null)
            {
                rx = gatt.getService(RBL_UART_UUID).getCharacteristic(RBL_RX_UUID);
            }

            // Setup notifications on RX characteristic changes (i.e. data received).
            // First call setCharacteristicNotification to enable notification.
            if (rx != null && !gatt.setCharacteristicNotification(rx, true)) {
                writeLine("Couldn't set notifications for RX characteristic!");
            }
            // Next update the RX characteristic's client descriptor to enable notifications.
            if (rx != null && rx.getDescriptor(CLIENT_UUID) != null) {
                BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!gatt.writeDescriptor(desc)) {
                    writeLine("Couldn't write RX client descriptor value!");
                }
            }
            else {
                writeLine("Couldn't get RX client descriptor!");
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


        // Called when a remote characteristic changes (like the RX characteristic).
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            ///when we read something, get the signal strength:

            gatt.readRemoteRssi();

            String data = characteristic.getStringValue(0);

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
    };

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

    // BTLE device scanning callback.
    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        // Called when a device is found.
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            writeLine("Found device: " + bluetoothDevice.getAddress());
            // Check if the device has the UART service.
            List<UUID> uuuidList = parseUUIDs(bytes);

           if (uuuidList.contains(UART_UUID) || uuuidList.contains(RBL_UART_UUID)) {
                // Found a device, stop the scan.
                adapter.stopLeScan(scanCallback);
                writeLine("Found UART service!");
                // Connect to the device.
                // Control flow will now go to the callback functions when BTLE events occur.
                gatt = bluetoothDevice.connectGatt(getApplicationContext(), false, callback);
                gatt.readRemoteRssi();
            }
            else {
                System.out.println("UART_UUID not found");
            }
        }
    };
    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,
                                    mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
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

        adapter = BluetoothAdapter.getDefaultAdapter();

        adapter.startLeScan(scanCallback);

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
        if (gatt != null) {
            // For better reliability be careful to disconnect and close the connection.
            gatt.disconnect();
            gatt.close();
            gatt = null;
            tx = null;
            rx = null;
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
