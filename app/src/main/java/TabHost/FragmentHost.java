package TabHost;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.astuetz.PagerSlidingTabStrip;
import com.tonydicola.bletest.app.R;

/**
 * Created by ammonrees on 11/10/14.
 */
public class FragmentHost extends Fragment {


    MyPagerAdapter mAdapter;
    ViewPager mPager;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_host, container, false);

        mAdapter = new MyPagerAdapter(getChildFragmentManager());
        mPager = (ViewPager) rootView.findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);

        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) rootView.findViewById(R.id.tabs);
        tabs.setViewPager(mPager);

       return rootView;
    }

    @Override
    public void onDestroy() {

        super.onDestroy();


    }
}
