package com.tonydicola.bletest.app;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.sql.Time;
import java.util.Date;

/**
 * Created by ammonrees on 1/3/15.
 */
public class GraphFragment extends Fragment implements DataListener {

    int fragVal;
    GraphView graph;
    LineGraphSeries<DataPoint> series;
    LineGraphSeries<DataPoint> pwmSeries;
    int lastPwm = 0;
    int lastWatts = 0;

    int index=0;
    StubActivity activity;

    public static GraphFragment init(int val) {
        GraphFragment graphFrag = new GraphFragment();
        Bundle args = new Bundle();
        args.putInt("val", val);
        graphFrag.setArguments(args);
        return graphFrag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragVal = getArguments() != null ? getArguments().getInt("val") : 1;

        activity = (StubActivity)getActivity();
        activity.registerListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_graph, container, false);
        graph = (GraphView) rootView.findViewById(R.id.graph);
        series = new LineGraphSeries<DataPoint>();
        pwmSeries = new LineGraphSeries<DataPoint>();
        pwmSeries.setColor(Color.GREEN);

        graph.addSeries(series);
        graph.getSecondScale().addSeries(pwmSeries);
        graph.getSecondScale().setMinY(0);
        graph.getSecondScale().setMaxY(100);
        graph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.GREEN);
        return rootView;
    }

    @Override
    public void onDataChanged() {

        double watts = activity.watts;
        double pwm = ((double)activity.pwmValue/254.0)*100.0;

        if(lastPwm == (int)pwm || lastWatts == (int)watts) {
            return;
        }

        lastPwm = (int)pwm;
        lastWatts = (int)watts;

        series.appendData(new DataPoint(index, watts),
                false, 1000);
        pwmSeries.appendData(new DataPoint(index, pwm),
                false, 1000);

        index++;
    }
}

