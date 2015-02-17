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
public class VoltageFragment extends Fragment implements DataListener{

    int fragVal;
    TextView current, volts, watts, highWatts, inputVolts, pwm;
    StubActivity activity;
    public static VoltageFragment init(int val) {
        VoltageFragment voltageFrag = new VoltageFragment();
        Bundle args = new Bundle();
        args.putInt("val", val);
        voltageFrag.setArguments(args);
        return voltageFrag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragVal = getArguments() != null ? getArguments().getInt("val") : 1;

        activity = (StubActivity)getActivity();

        activity.registerListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        activity.unregisterListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_voltage, container, false);

        Typeface tf = Typeface.createFromAsset(getActivity().getAssets(), "fonts/Roboto-Thin.ttf");
        //  Typeface tf2 = Typeface.createFromAsset(context.getAssets(), "fonts/RobotoSlab-Bold.ttf");
        Typeface tf3 = Typeface.createFromAsset(getActivity().getAssets(), "fonts/RobotoSlab-Regular.ttf");

        current = (TextView) rootView.findViewById(R.id.currentText);
        current.setTypeface(tf);
        volts = (TextView) rootView.findViewById(R.id.OutputVoltText);
        volts.setTypeface(tf);
        inputVolts = (TextView) rootView.findViewById(R.id.inputVoltsTest);
        inputVolts.setTypeface(tf);
        watts = (TextView) rootView.findViewById(R.id.wattsText);
        watts.setTypeface(tf);
        highWatts = (TextView) rootView.findViewById(R.id.HighWattsText);
        highWatts.setTypeface(tf);
        pwm = (TextView) rootView.findViewById(R.id.pwmText);
        pwm.setTypeface(tf);

        return rootView;
    }

    @Override
    public void onDataChanged() {
        StubActivity.setAmps(current, activity.current);
        StubActivity.setVolts(volts, activity.voltage);
        StubActivity.setVolts(inputVolts, activity.inputVolts);
        StubActivity.setWatts(watts, activity.watts);
        StubActivity.setWatts(highWatts, activity.highWatts);
        StubActivity.setPercentage(pwm, ((double)activity.pwmValue / 254.0)*100.0);
    }
}
