package com.tonydicola.bletest.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by ammonrees on 1/3/15.
 */
public class GraphFragment extends Fragment {

    int fragVal;

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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_voltage, container, false);






        return rootView;
    }
}

