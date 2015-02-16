package com.tonydicola.bletest.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Handler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class MainActivity extends Activity {

    private GpsService gpsService;
    // location stuff
    private TextView currentField;
    private TextView pressureField;
    private TextView temperatureField;
    private TextView distanceField;
    private TextView connectedField;
    private TextView signalField;
    private TextView voltField;
    private TextView wattField;
    private TextView highWattField;
    private TextView systemWattField;
    private TextView systemVoltField;
    private TextView pwmValueField;

    private boolean mIsBound;

    private boolean tracking;

    final Messenger mMessenger = new Messenger(new IncomingHandler());
    Messenger mService = null;

    private String jsonBuffer="";

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

            System.out.println("Client received msg from service: "+ String.valueOf(msg.what));
            Bundle bundle = msg.getData();
            if(bundle != null)
            {
                if(msg.what == GpsService.MSG_SET_SIGNAL) {
                    signalField.setText(String.valueOf(msg.arg1));
                    return;
                }

                String jsonStr = bundle.getString("json");

                if(jsonStr == null)
                    return;

                JSONObject json = null;
                try {
                    json = new JSONObject(jsonStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                try {
                    if (json.has("c")) {
                        double tc = json.getDouble("c");
                        currentChanged(tc);
                    }
                    if (json.has("p")) {
                        double tp = json.getDouble("p");
                        pressureChanged(tp);
                    }
                    if (json.has("t")) {
                        double tt = json.getDouble("t");
                        temperatureChanged(tt);
                    }
                    if (json.has("v")) {
                        double tt = json.getDouble("v");
                        System.out.println("volts: " + String.valueOf(tt));
                        voltField.setText(String.valueOf(tt));
                    }
                    if (json.has("w")) {
                        double tt = json.getDouble("w");
                        System.out.println("watts: " + String.valueOf(tt));
                        wattField.setText(String.valueOf(tt));
                    }
                    if (json.has("hw")) {
                        double tt = json.getDouble("hw");
                        System.out.println("highwatts: " + String.valueOf(tt));
                        highWattField.setText(String.valueOf(tt));
                    }
                    if (json.has("sw")) {
                        int sw = json.getInt("sw");
                        System.out.println("systemWatts: " + String.valueOf(sw));
                        systemWattField.setText(String.valueOf(sw));
                    }
                    if (json.has("sv")) {
                        int sw = json.getInt("sv");
                        System.out.println("system Volts: " + String.valueOf(sw));
                        systemVoltField.setText(String.valueOf(sw));
                    }
                    if (json.has("pwm")) {
                        int sw = json.getInt("pwm");
                        System.out.println("pwm: " + String.valueOf(sw));
                        pwmValueField.setText(String.valueOf(sw));
                    }
                }
                catch (JSONException e)
                {
                    e.printStackTrace();
                }
                return;
            }


        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mService = new Messenger(service);
            System.out.println("Client Attached.");

            try {
                Message msg = Message.obtain(null, GpsService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mService = null;
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).

        Intent intent = new Intent(MainActivity.this,
                GpsService.class);

        startService(intent);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentField = (TextView) findViewById(R.id.CurrentField);
        pressureField = (TextView) findViewById(R.id.PressureField);
        temperatureField = (TextView) findViewById(R.id.TemperatureField);
        distanceField = (TextView) findViewById(R.id.distance);
        connectedField = (TextView) findViewById(R.id.ConnectedField);
        signalField = (TextView) findViewById(R.id.SignalStrengthField);
        voltField = (TextView) findViewById(R.id.VoltsField);
        wattField = (TextView) findViewById(R.id.WattsField);
        highWattField = (TextView) findViewById(R.id.HighWattsField);
        systemWattField = (TextView) findViewById(R.id.SystemWattsField);
        systemVoltField = (TextView) findViewById(R.id.SystemVoltsField);
        pwmValueField = (TextView) findViewById(R.id.PWMValueField);
        doBindService();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
    }

    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();

    }

    // Handler for mouse click on the send button.
    public void sendClick(View view) {
       /* String message = input.getText().toString();
        if (tx == null || message == null || message.isEmpty()) {
            // Do nothing if there is no device or message to send.
            return;
        }
        // Update TX characteristic value.  Note the setValue overload that takes a byte array must be used.
        tx.setValue(message.getBytes(Charset.forName("UTF-8")));
        if (gatt.writeCharacteristic(tx)) {
            writeLine("Sent: " + message);
        }
        else {
            writeLine("Couldn't write TX characteristic!");
        }*/
    }

    public void toggleTracking(View view) {
        Message msg = Message.obtain(null, GpsService.MSG_SET_TRACKING, !tracking ? 1:0, 0);
        msg.replyTo = mMessenger;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Button startTracking = (Button) findViewById(R.id.startTracking);

        startTracking.setText(tracking == true ? "Stopping" : "Starting");
    }

    private void trackingChanged(boolean t)
    {
        tracking = t;

        Button startTracking = (Button) findViewById(R.id.startTracking);

        startTracking.setText(tracking == true ? "Stop Tracking" : "Start Tracking");
    }

    // Write some text to the messages text view.
    // Care is taken to do this on the main UI thread so writeLine can be called
    // from any thread (like the BTLE callback).
    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {


            }
        });
    }



    // Boilerplate code from the activity creation:

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void distanceChanged(double distance) {
        distanceField.setText(String.valueOf(Math.ceil(distance)));
    }

    public void altitudeChanged(double altitude) {

    }

    public void currentChanged(double current) {
        currentField.setText(String.valueOf(Math.ceil(current)));
    }

    public void pressureChanged(double pressure) {
        pressureField.setText(String.valueOf(Math.ceil(pressure)));
    }

    public void temperatureChanged(double currentTemperature) {
        temperatureField.setText(String.valueOf((int)Math.ceil(currentTemperature * 9 / 5 + 32)) + "F");
    }

    public void connectionChanged(boolean connected) {
        System.out.println("Client changing connected status to " + String.valueOf(connected));
        connectedField.setText(connected == true ? "connected" : "disconnected");
    }
}
