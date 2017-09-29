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
import com.frostwire.android.gui.fragments.TransferDetailStatusFragment;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractFragment;

public class TransferDetailActivity extends AbstractActivity {

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        ViewPager mViewPager = findViewById(R.id.transfer_detail_viewpager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setCurrentItem(1);

        TabLayout tabLayout = findViewById(R.id.transfer_detail_tab_layout);
        tabLayout.setupWithViewPager(mViewPager);
    }

    public static class PlaceholderFragment extends Fragment {

        private static final String ARG_SECTION_NUMBER = "section_number";

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
            return inflater.inflate(R.layout.fragment_transfer_detail_label, container, false);
        }
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public AbstractFragment getItem(int position) {
            switch(position) {
                case 0: return TransferDetailDetailsFragment.newInstance(getString(R.string.details).toUpperCase());
                case 1: return TransferDetailStatusFragment.newInstance(getString(R.string.status).toUpperCase());
                case 2: return TransferDetailFilesFragment.newInstance(getString(R.string.files).toUpperCase());
                case 3: return TransferDetailTrackersFragment.newInstance(getString(R.string.trackers).toUpperCase());
                case 4: return TransferDetailPeersFragment.newInstance(getString(R.string.peers).toUpperCase());
                case 5: return TransferDetailPiecesFragment.newInstance(getString(R.string.pieces).toUpperCase());
                default: return TransferDetailStatusFragment.newInstance(getString(R.string.status).toUpperCase());
            }
        }

        @Override
        public int getCount() {
            return 6;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.details).toUpperCase();
                case 1:
                    return getString(R.string.status).toUpperCase();
                case 2:
                    return getString(R.string.files).toUpperCase();
                case 3:
                    return getString(R.string.trackers).toUpperCase();
                case 4:
                    return getString(R.string.peers).toUpperCase();
                case 5:
                    return getString(R.string.pieces).toUpperCase();
                default: return getString(R.string.status).toUpperCase();
            }
        }
    }
}
