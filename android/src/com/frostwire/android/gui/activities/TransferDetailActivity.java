/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.activities;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;

import com.frostwire.android.R;
import com.frostwire.android.gui.fragments.TransferDetailDetailsFragment;
import com.frostwire.android.gui.fragments.TransferDetailFilesFragment;
import com.frostwire.android.gui.fragments.TransferDetailPeersFragment;
import com.frostwire.android.gui.fragments.TransferDetailPiecesFragment;
import com.frostwire.android.gui.fragments.TransferDetailStatusFragment;
import com.frostwire.android.gui.fragments.TransferDetailTrackersFragment;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;

public class TransferDetailActivity extends AbstractActivity implements TimerObserver {

    private OnPageChangeListener onPageChangeListener;
    private TimerSubscription subscription;

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String infoHash = getIntent().getStringExtra("infoHash");
        if (infoHash == null || "".equals(infoHash)) {
            throw new RuntimeException("Invalid infoHash received");
        }
        UIBittorrentDownload uiBittorrentDownload = (UIBittorrentDownload)
                TransferManager.instance().getBittorrentDownload(infoHash);
        if (uiBittorrentDownload == null) {
            throw new RuntimeException("Could not find matching transfer for infoHash:" + infoHash);
        }
        SparseArray<String> tabTitles = new SparseArray<>(6);
        tabTitles.put(R.string.files, getString(R.string.files));
        tabTitles.put(R.string.status, getString(R.string.status));
        tabTitles.put(R.string.details, getString(R.string.details));
        tabTitles.put(R.string.trackers, getString(R.string.trackers));
        tabTitles.put(R.string.peers, getString(R.string.peers));
        tabTitles.put(R.string.pieces, getString(R.string.pieces));
        SectionsPagerAdapter mSectionsPagerAdapter =
                new SectionsPagerAdapter(getFragmentManager(),
                        uiBittorrentDownload,
                        tabTitles);
        onPageChangeListener = new OnPageChangeListener(mSectionsPagerAdapter.getFragments());
        ViewPager mViewPager = findViewById(R.id.transfer_detail_viewpager);
        if (mViewPager != null) {
            mViewPager.setAdapter(mSectionsPagerAdapter);
            // TODO: remember last selected
            mViewPager.setCurrentItem(0);
            TabLayout tabLayout = findViewById(R.id.transfer_detail_tab_layout);
            tabLayout.setupWithViewPager(mViewPager);
        }
        mViewPager.addOnPageChangeListener(onPageChangeListener);

        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }

        subscription = TimerService.subscribe(this, 2);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    @Override
    public void onTime() {
        if (onPageChangeListener == null) {
            return;
        }
        AbstractTransferDetailFragment currentFragment = onPageChangeListener.getCurrentFragment();
        if (currentFragment == null) {
            return;
        }
        try {
            currentFragment.onTime();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static class SectionsPagerAdapter extends FragmentPagerAdapter {

        private final AbstractTransferDetailFragment[] detailFragments;
        private final SparseArray<String> tabTitles;

        SectionsPagerAdapter(FragmentManager fm,
                             UIBittorrentDownload uiBittorrentDownload,
                             SparseArray<String> tabTitles) {
            super(fm);
            this.tabTitles = tabTitles;
            detailFragments = initFragments(uiBittorrentDownload);
        }

        private AbstractTransferDetailFragment[] initFragments(UIBittorrentDownload uiBittorrentDownload) {
            // to change the order of the tabs, add/remove tabs, just maintain here.
            return new AbstractTransferDetailFragment[]{
                    new TransferDetailFilesFragment().init(tabTitles.get(R.string.files), uiBittorrentDownload),
                    new TransferDetailStatusFragment().init(tabTitles.get(R.string.status), uiBittorrentDownload),
                    new TransferDetailDetailsFragment().init(tabTitles.get(R.string.details), uiBittorrentDownload),
                    new TransferDetailTrackersFragment().init(tabTitles.get(R.string.trackers), uiBittorrentDownload),
                    new TransferDetailPeersFragment().init(tabTitles.get(R.string.peers), uiBittorrentDownload),
                    new TransferDetailPiecesFragment().init(tabTitles.get(R.string.pieces), uiBittorrentDownload)
            };
        }

        @Override
        public AbstractFragment getItem(int position) {
            return detailFragments[position];
        }

        @Override
        public int getCount() {
            return detailFragments.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return detailFragments[position].getTabTitle().toUpperCase();
        }

        public AbstractTransferDetailFragment[] getFragments() {
            return detailFragments;
        }
    }

    private static final class OnPageChangeListener implements ViewPager.OnPageChangeListener {

        private AbstractTransferDetailFragment[] detailFragments;
        private AbstractTransferDetailFragment currentFragment;

        OnPageChangeListener(AbstractTransferDetailFragment[] detailFragments) {
            this.detailFragments = detailFragments;
        }

        public AbstractTransferDetailFragment getCurrentFragment() {
            return currentFragment;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            try {
                currentFragment = detailFragments[position];
                currentFragment.onTime();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }
}
