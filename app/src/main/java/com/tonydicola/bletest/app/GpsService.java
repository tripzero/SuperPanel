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
    private String provider;

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
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // BTLE state
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    private boolean mConnected;

    // Main BTLE device callback where much of the logic occurs.
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        // Called whenever the device connection state changes, i.e. from disconnected to connected.
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                writeLine("Connected!");
                mConnected = true;
                connectionChanged(true);

                // Discover services.
                if (!gatt.discoverServices()) {
                    writeLine("Failed to start discovering services!");
                }
            }
            else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                writeLine("Disconnected!");
                mConnected = false;
                connectionChanged(false);
                gatt.connect();
            }
            else {
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
            // Save reference to each characteristic.
            tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
            rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);
            // Setup notifications on RX characteristic changes (i.e. data received).
            // First call setCharacteristicNotification to enable notification.
            if (!gatt.setCharacteristicNotification(rx, true)) {
                writeLine("Couldn't set notifications for RX characteristic!");
            }
            // Next update the RX characteristic's client descriptor to enable notifications.
            if (rx.getDescriptor(CLIENT_UUID) != null) {
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

        // Called when a remote characteristic changes (like the RX characteristic).
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            ///when we read something, get the signal strength:

            gatt.readRemoteRssi();

            String data = characteristic.getStringValue(0);

            System.out.println("received from device: " + data);
            rawJSon(data);
        }
    };

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
            if (parseUUIDs(bytes).contains(UART_UUID)) {
                // Found a device, stop the scan.
                adapter.stopLeScan(scanCallback);
                writeLine("Found UART service!");
                // Connect to the device.
                // Control flow will now go to the callback functions when BTLE events occur.
                gatt = bluetoothDevice.connectGatt(getApplicationContext(), false, callback);
                gatt.readRemoteRssi();
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

    private void connectionChanged(boolean value) {
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
        System.out.println("sending raw json to clients:" +value);
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

        Criteria criteria = new Criteria();

        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);

        // Initialize the location fields
        if (location != null) {
            System.out.println("Provider " + provider + " has been selected.");
            onLocationChanged(location);
        }

        locationManager.requestLocationUpdates(provider, 10000, 10, this);

        tts = new TextToSpeech(this, this);

        adapter = BluetoothAdapter.getDefaultAdapter();

        adapter.startLeScan(scanCallback);

        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {

        System.out.println("I'm going the distance: " + location.toString());
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

            //distanceChanged(distance);
            //altitudeChanged(location.getAltitude());

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
