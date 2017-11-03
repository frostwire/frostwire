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
import com.frostwire.util.Logger;

public class TransferDetailActivity extends AbstractActivity implements TimerObserver {
    private Logger LOG = Logger.getLogger(TransferDetailActivity.class);
    private OnPageChangeListener onPageChangeListener;
    private ViewPager viewPager;
    private TimerSubscription subscription;
    private UIBittorrentDownload uiBittorrentDownload;
    private SparseArray<String> tabTitles;
    private AbstractTransferDetailFragment[] detailFragments;

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        super.initComponents(savedInstanceState);

        initUIBittorrentDownload();
        initTabTitles();
        initFragments();

        if (detailFragments == null || detailFragments.length <= 0) {
            throw new RuntimeException("check your logic: can't initialize components without initialized fragments");
        }
        onPageChangeListener = new OnPageChangeListener(detailFragments);
        SectionsPagerAdapter mSectionsPagerAdapter =
                new SectionsPagerAdapter(getFragmentManager(), detailFragments);
        viewPager = findViewById(R.id.transfer_detail_viewpager);
        if (viewPager != null) {
            viewPager.setAdapter(mSectionsPagerAdapter);
            TabLayout tabLayout = findViewById(R.id.transfer_detail_tab_layout);
            tabLayout.setupWithViewPager(viewPager);
            viewPager.setCurrentItem(0);
            if (savedInstanceState != null) {
                int lastSelectedTabIndex = savedInstanceState.getInt("lastSelectedTabIndex", -1);
                if (lastSelectedTabIndex != -1) {
                    viewPager.setCurrentItem(lastSelectedTabIndex);
                }
            }
            viewPager.addOnPageChangeListener(onPageChangeListener);
        }
    }

    private void initUIBittorrentDownload() {
        String infoHash = getIntent().getStringExtra("infoHash");
        if (infoHash == null || "".equals(infoHash)) {
            throw new RuntimeException("Invalid infoHash received");
        }
        uiBittorrentDownload = (UIBittorrentDownload)
                TransferManager.instance().getBittorrentDownload(infoHash);
        if (uiBittorrentDownload == null) {
            throw new RuntimeException("Could not find matching transfer for infoHash:" + infoHash);
        }
    }

    private void initTabTitles() {
        tabTitles = new SparseArray<>(6);
        tabTitles.put(R.string.files, getString(R.string.files));
        tabTitles.put(R.string.status, getString(R.string.status));
        tabTitles.put(R.string.details, getString(R.string.details));
        tabTitles.put(R.string.trackers, getString(R.string.trackers));
        tabTitles.put(R.string.peers, getString(R.string.peers));
        tabTitles.put(R.string.pieces, getString(R.string.pieces));
    }

    private void initFragments() {
        if (uiBittorrentDownload == null) {
            throw new RuntimeException("check your logic: can't init fragments without an uiBitTorrentDownload instance set");
        }
        if (tabTitles == null || tabTitles.size() <= 0) {
            throw new RuntimeException("check your logic: can't init fragments without initializing tab titles");
        }
        // to change the order of the tabs, add/remove tabs, just maintain here.
        detailFragments = new AbstractTransferDetailFragment[]{
                new TransferDetailFilesFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailStatusFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailDetailsFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailTrackersFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailPeersFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailPiecesFragment().init(this, tabTitles, uiBittorrentDownload)
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        if (uiBittorrentDownload == null) {
            throw new RuntimeException("No UIBittorrent download, unacceptable");
        }
        reInitFragments();
        subscription = TimerService.subscribe(this, 2);
        LOG.info("onResume() - subscribed to timer");
    }

    private void reInitFragments() {
        // TO TEST: getting rid of this method and just creating new fragments altogether on rotation
        if (detailFragments == null || detailFragments.length <= 0) {
            throw new RuntimeException("check your logic: no fragments to re-initialize");
        }
        if (tabTitles == null || tabTitles.size() <= 0) {
            throw new RuntimeException("check your logic: tabTitles not initialized");
        }
        for (AbstractTransferDetailFragment fragment : detailFragments) {
            fragment.init(this, tabTitles, uiBittorrentDownload);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
            LOG.info("onPause() - un-subscribed from timer");
        }
    }

    @Override
    public void onTime() {
        if (onPageChangeListener == null) {
            LOG.info("onTime() aborted, no onPageChangeListener");
            return;
        }
        AbstractTransferDetailFragment currentFragment = onPageChangeListener.getCurrentFragment();
        if (currentFragment == null) {
            LOG.info("onTime() aborted, no currentFragment");
            return;
        }
        try {
            LOG.info("onTime()");
            currentFragment.onTime();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("lastSelectedTabIndex", viewPager.getCurrentItem());
    }

    private static class SectionsPagerAdapter extends FragmentPagerAdapter {

        private final AbstractTransferDetailFragment[] detailFragments;

        SectionsPagerAdapter(FragmentManager fm,
                             AbstractTransferDetailFragment[] detailFragments) {
            super(fm);
            this.detailFragments = detailFragments;
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
        private static Logger LOG = Logger.getLogger(OnPageChangeListener.class);
        private AbstractTransferDetailFragment[] detailFragments;
        private AbstractTransferDetailFragment currentFragment;

        OnPageChangeListener(AbstractTransferDetailFragment[] detailFragments) {
            this.detailFragments = detailFragments;
            currentFragment = detailFragments[0];
        }

        public AbstractTransferDetailFragment getCurrentFragment() {
            if (currentFragment == null && detailFragments != null && detailFragments.length > 0) {
                currentFragment = detailFragments[0];
            }
            return currentFragment;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            try {
                currentFragment = detailFragments[position];
            } catch (Throwable t) {
                currentFragment = null;
                t.printStackTrace();
                return;
            }
            try {
                LOG.info("onPageSelected(" + position + ") -> " + currentFragment.getTabTitle());
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
