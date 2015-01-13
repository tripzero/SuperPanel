package TabHost;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.tonydicola.bletest.app.GraphFragment;
import com.tonydicola.bletest.app.LocationFragment;
import com.tonydicola.bletest.app.VoltageFragment;

/**
 * Created by ammonrees on 11/10/14.
 */
public class MyPagerAdapter extends FragmentPagerAdapter {

    static final int ITEMS = 3;

    private final String[] TITLES = {"Dashboard", "Super Panel","Panel Graphs"};


    public MyPagerAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return TITLES[position];
    }

    @Override
    public int getCount() {
        return ITEMS;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0: // Fragment # 0 - This will show image
                return LocationFragment.init(position);
            case 1: // Fragment # 1 - This will show image
                return VoltageFragment.init(position);
            case 2: // Fragment # 1 - This will show image
                return GraphFragment.init(position);
            default:
            return LocationFragment.init(position);
        }
    }
}




