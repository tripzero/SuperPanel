package com.tonydicola.bletest.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import TabHost.FragmentHost;


interface DataListener
{
    void onDataChanged();
}

public class StubActivity extends BaseActivity {

    public double current;
    public double voltage;
    public double watts;
    public double highWatts;
    public int inputVolts;
    public int pwmValue;
    public int bleDeviceSignal;

    public double distance;
    public double latitude;
    public double longitude;
    public double speed;

    public String location() {
        if(fix)
            return  String.valueOf(latitude) + ", " + String.valueOf(longitude);
        else
            return "Searching...";
    };

    public boolean fix;

    private boolean mIsBound;

    final Messenger mMessenger = new Messenger(new IncomingHandler());
    Messenger mService = null;

    List<DataListener> listeners = new ArrayList<DataListener>();

    static <T> String strWithUnit(T str, String unit)
    {
        return String.valueOf(str) + " " + unit;
    }

    static void setVolts(TextView txt, double volts)
    {
        txt.setText(strWithUnit(volts, "V"));
    }

    static void setAmps(TextView txt, double amps)
    {
        txt.setText(strWithUnit(amps, "mA"));
    }

    static void setWatts(TextView txt, double watts)
    {
        txt.setText(strWithUnit(watts, "W"));
    }

    static void setPercentage(TextView txt, double percent)
    {
        txt.setText(String.format("%.2f", percent) + " %");
    }

    public void registerListener(DataListener l)
    {
        System.out.println(listeners);
        listeners.add(l);
    }

    public void unregisterListener(DataListener l)
    {
        listeners.remove(l);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        doBindService();
        //Set Icon in Toolbar
//        setActionBarIcon(R.drawable.ic_launcher);
        //Create Fragment and commit to Activity
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.content, new FragmentHost());
        ft.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    //Inflate the view (Empty Container to hold Fragment)
    @Override
    protected int getLayoutResource() {
        return R.layout.activity_stub;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_stub, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if(bundle != null)
            {
                if(msg.what == GpsService.MSG_SET_SIGNAL) {
                    bleDeviceSignal = msg.arg1;
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
                        current = tc;
                    }
                    if (json.has("p")) {
                        double tp = json.getDouble("p");
                    }
                    if (json.has("t")) {
                        double tt = json.getDouble("t");
                    }
                    if (json.has("v")) {
                        double tt = json.getDouble("v");
                        voltage = tt;
                    }
                    if (json.has("w")) {
                        double tt = json.getDouble("w");
                        watts = tt;
                    }
                    if (json.has("hw")) {
                        double tt = json.getDouble("hw");
                        highWatts = tt;
                    }
                    if (json.has("sv")) {
                        inputVolts = json.getInt("sv");
                    }
                    if (json.has("pwm")) {
                        int sw = json.getInt("pwm");
                        System.out.println("pwm: " + String.valueOf(sw));
                        pwmValue = sw;
                    }
                    if (json.has(GpsService.GPS_LAT)) {
                        latitude = json.getDouble(GpsService.GPS_LAT);
                    }
                    if (json.has(GpsService.GPS_LON)) {
                        longitude = json.getDouble(GpsService.GPS_LON);
                    }
                    if (json.has(GpsService.GPS_SPD)) {
                        speed = json.getDouble(GpsService.GPS_SPD);
                    }
                    if (json.has(GpsService.GPS_FIX)) {
                        fix = json.getInt(GpsService.GPS_FIX) == 1;
                    }

                    for(DataListener i : listeners)
                    {
                        i.onDataChanged();
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

        Intent intent = new Intent(StubActivity.this,
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
}
