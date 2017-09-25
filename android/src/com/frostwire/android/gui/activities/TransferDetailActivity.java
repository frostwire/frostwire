package com.frostwire.android.gui.activities;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;
import com.frostwire.android.gui.fragments.TransferDetailDetailsFragment;
import com.frostwire.android.gui.fragments.TransferDetailFilesFragment;
import com.frostwire.android.gui.fragments.TransferDetailPeersFragment;
import com.frostwire.android.gui.fragments.TransferDetailPiecesFragment;
import com.frostwire.android.gui.fragments.TransferDetailTrackersFragment;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractFragment;

public class TransferDetailActivity extends AbstractActivity {
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        mViewPager = findViewById(R.id.transfer_detail_viewpager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.transfer_detail_tab_layout);
        tabLayout.setupWithViewPager(mViewPager);
    }


    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_transfer_detail_label, container, false);
            return rootView;
        }
    }
    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public AbstractFragment getItem(int position) {
            AbstractFragment fragment;
            switch(position) {
                case 0: return TransferDetailDetailsFragment.newInstance("Details");
                case 1: return TransferDetailFilesFragment.newInstance("Files");
                case 2: return TransferDetailTrackersFragment.newInstance("Trackers");
                case 3: return TransferDetailPeersFragment.newInstance("Peers");
                case 4: return TransferDetailPiecesFragment.newInstance("Pieces");
                default: return TransferDetailDetailsFragment.newInstance("Details");
            }
        }

        @Override
        public int getCount() {
            return 5;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "DETAILS";
                case 1:
                    return "FILES";
                case 2:
                    return "TRACKERS";
                case 3:
                    return "PEERS";
                case 4:
                    return "PIECES";
            }
            return null;
        }
    }
}
