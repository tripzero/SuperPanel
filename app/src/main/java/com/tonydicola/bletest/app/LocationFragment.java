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
public class LocationFragment extends Fragment {

    private TextView pressureField;
    private TextView temperatureField;
    private TextView distanceField;
    private TextView connectedField;
    private TextView signalField;

    int fragVal;
    TextView txt1,txt2,txt3,txt4,txt5;


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


        txt1 = (TextView) rootView.findViewById(R.id.textView);
        txt1.setTypeface(tf);
        txt2 = (TextView) rootView.findViewById(R.id.textView2);
        txt2.setTypeface(tf);
        txt3 = (TextView) rootView.findViewById(R.id.textView3);
        txt3.setTypeface(tf);
        txt4 = (TextView) rootView.findViewById(R.id.textView4);
        txt4.setTypeface(tf);
        txt5 = (TextView) rootView.findViewById(R.id.textView5);
        txt5.setTypeface(tf);

        return rootView;
    }
}