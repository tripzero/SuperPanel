package com.tonydicola.bletest.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class GpsService extends Service implements LocationListener, TextToSpeech.OnInitListener {

    private boolean tracking;
    private double distance;
    private double oldLat;
    private double oldLon;

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
        tracking = t;

        if (!tts.isSpeaking()) {
            tts.speak("You have travelled " + String.valueOf(Math.ceil(distance/1609)) + " miles", TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public GpsService() {

    }

    public class GpsBinder extends Binder {
        GpsService getService() {
            return GpsService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);

        // Initialize the location fields
        if (location != null) {
            System.out.println("Provider " + provider + " has been selected.");
            onLocationChanged(location);
        }

        locationManager.requestLocationUpdates(provider, 400, 1, this);

        tts = new TextToSpeech(this, this);

        return START_STICKY;
    }

    private final IBinder mBinder = new GpsBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onLocationChanged(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        if(oldLat == 0) oldLat = lat;
        if(oldLon == 0) oldLon = lon;

        if(tracking) {

            double dist = calcDistance(lat, lon, oldLat, oldLon);

            System.out.println("dist: " + String.valueOf(dist));

            if (dist >= 10) {
                distance += dist;

                if(distance % 1609 < 19)

                    if (!tts.isSpeaking()) {
                        tts.speak("You have travelled " + String.valueOf(Math.ceil(distance/1609)) + " miles", TextToSpeech.QUEUE_FLUSH, null);
                    }

                oldLat = lat;
                oldLon = lon;
            }
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
