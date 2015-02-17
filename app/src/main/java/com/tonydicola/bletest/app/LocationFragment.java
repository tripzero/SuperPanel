package com.tonydicola.bletest.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by ammonrees on 1/2/15.
 */
public class LocationFragment extends Fragment implements DataListener {

    private TextView temperatureField;
    private TextView distanceField;
    private TextView connectedField;
    private TextView signalField;
    private TextView locationField;

    int fragVal;

    StubActivity activity;

    public static LocationFragment init(int val) {
        LocationFragment locationFrag = new LocationFragment();
        // Supply val input as an argument.
        Bundle args = new Bundle();
        args.putInt("val", val);
        locationFrag.setArguments(args);
        return locationFrag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragVal = getArguments() != null ? getArguments().getInt("val") : 1;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_location, container, false);

        Typeface tf = Typeface.createFromAsset(getActivity().getAssets(), "fonts/Roboto-Thin.ttf");
        //  Typeface tf2 = Typeface.createFromAsset(context.getAssets(), "fonts/RobotoSlab-Bold.ttf");
        Typeface tf3 = Typeface.createFromAsset(getActivity().getAssets(), "fonts/RobotoSlab-Regular.ttf");

        temperatureField = (TextView) rootView.findViewById(R.id.TemperatureField);
        distanceField = (TextView) rootView.findViewById(R.id.distance);
        connectedField = (TextView) rootView.findViewById(R.id.ConnectedField);
        signalField = (TextView) rootView.findViewById(R.id.SignalStrengthField);


        temperatureField = (TextView) rootView.findViewById(R.id.temperatureText);
        temperatureField.setTypeface(tf);
        distanceField = (TextView) rootView.findViewById(R.id.distanceText);
        distanceField.setTypeface(tf);
        connectedField = (TextView) rootView.findViewById(R.id.connectedText);
        connectedField.setTypeface(tf);
        signalField = (TextView) rootView.findViewById(R.id.signalText);
        signalField.setTypeface(tf);
        locationField = (TextView) rootView.findViewById(R.id.locationText);
        locationField.setTypeface(tf);

        return rootView;
    }

    @Override
    public void onDataChanged() {
        locationField.setText(activity.location());
    }
}