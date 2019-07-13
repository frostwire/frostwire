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

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.util.SparseArray;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.fragments.TransferDetailDetailsFragment;
import com.frostwire.android.gui.fragments.TransferDetailFilesFragment;
import com.frostwire.android.gui.fragments.TransferDetailFragment;
import com.frostwire.android.gui.fragments.TransferDetailPeersFragment;
import com.frostwire.android.gui.fragments.TransferDetailPiecesFragment;
import com.frostwire.android.gui.fragments.TransferDetailStatusFragment;
import com.frostwire.android.gui.fragments.TransferDetailTrackersFragment;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.FragmentPagerAdapter;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

import androidx.viewpager.widget.ViewPager;

public class TransferDetailActivity extends AbstractActivity implements TimerObserver {
    private TimerSubscription subscription;
    private UIBittorrentDownload uiBittorrentDownload;
    private SparseArray<String> tabTitles;
    private AbstractTransferDetailFragment[] detailFragments;
    private TransferDetailFragment transferDetailFragment;
    private int lastSelectedTabIndex;

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        super.initComponents(savedInstanceState);
        if (!initUIBittorrentDownload()) {
            UIUtils.showShortMessage(this, R.string.could_not_open_transfer_detail_invalid_infohash);
            finish();
            return;
        }
        transferDetailFragment = findFragment(R.id.fragment_transfer_detail);
        if (transferDetailFragment != null) {
            transferDetailFragment.setUiBittorrentDownload(uiBittorrentDownload);
        }
        initTabTitles();
        initFragments();
        if (detailFragments == null || detailFragments.length <= 0) {
            throw new RuntimeException("check your logic: can't initialize components without initialized fragments");
        }
        if (savedInstanceState != null) {
            lastSelectedTabIndex = savedInstanceState.getInt("lastSelectedTabIndex", -1);
        } else {
            lastSelectedTabIndex = ConfigurationManager.instance().getInt(Constants.PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX);
        }
        OnPageChangeListener onPageChangeListener = new OnPageChangeListener(this);
        SectionsPagerAdapter mSectionsPagerAdapter =
                new SectionsPagerAdapter(getFragmentManager(), detailFragments);
        ViewPager viewPager = findViewById(R.id.transfer_detail_viewpager);

        if (viewPager != null) {
            viewPager.clearOnPageChangeListeners();
            viewPager.setAdapter(mSectionsPagerAdapter);
            viewPager.setCurrentItem(lastSelectedTabIndex == -1 ? 0 : lastSelectedTabIndex);
            viewPager.addOnPageChangeListener(onPageChangeListener);
            TabLayout tabLayout = findViewById(R.id.transfer_detail_tab_layout);
            tabLayout.setupWithViewPager(viewPager);
        } else {
            throw new RuntimeException("initComponents() Could not get viewPager");
        }
    }

    private boolean initUIBittorrentDownload() {
        String infoHash = getIntent().getStringExtra("infoHash");
        if (uiBittorrentDownload == null && (infoHash == null || "".equals(infoHash))) {
            return false;
        }
        if (uiBittorrentDownload == null && !"".equals(infoHash)) {
            uiBittorrentDownload = (UIBittorrentDownload)
                    TransferManager.instance().getBittorrentDownload(infoHash);
        }
        return uiBittorrentDownload != null;
    }

    private void initTabTitles() {
        tabTitles = new SparseArray<>(6);
        tabTitles.put(R.string.files, getString(R.string.files));
        tabTitles.put(R.string.pieces, getString(R.string.pieces));
        tabTitles.put(R.string.status, getString(R.string.status));
        tabTitles.put(R.string.details, getString(R.string.details));
        tabTitles.put(R.string.trackers, getString(R.string.trackers));
        tabTitles.put(R.string.peers, getString(R.string.peers));
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
                new TransferDetailPiecesFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailStatusFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailDetailsFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailTrackersFragment().init(this, tabTitles, uiBittorrentDownload),
                new TransferDetailPeersFragment().init(this, tabTitles, uiBittorrentDownload)
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
        subscription = TimerService.subscribe(this, 2);
        onTime();
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
        if (subscription == null || !subscription.isSubscribed()) {
            return;
        }
        if (detailFragments == null || detailFragments.length == 0) {
            return;
        }
        if (lastSelectedTabIndex < 0 || lastSelectedTabIndex > detailFragments.length - 1) {
            return;
        }
        AbstractTransferDetailFragment currentFragment = detailFragments[lastSelectedTabIndex];
        if (currentFragment == null) {
            return;
        }
        if (!currentFragment.isAdded()) {
            Fragment correspondingActiveFragment = getCorrespondingActiveFragment(currentFragment);
            if (correspondingActiveFragment == null) {
                return; // definitively not added yet
            }
            detailFragments[lastSelectedTabIndex]=(AbstractTransferDetailFragment) correspondingActiveFragment;
            currentFragment = detailFragments[lastSelectedTabIndex];
        }
        if (transferDetailFragment != null) {
            transferDetailFragment.updatePauseResumeSeedMenuAction();
        }

        try {
            currentFragment.onTime();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Fragment rotation ends up with initialized detail fragments not added,
     * it seems the SectionsPageAdapter doesn't properly tag the fragments
     * and we have to manually find the corresponding added fragment
     * in the list keep by AbstractFragment's getFragments()
     *
     * We receive a fragment whose .isAdded() method returns false and we
     * look into our tracked list of fragments for an equivalent instance that
     * is marked as added and return it.
     *
     * We'll then replace that instance in our detailFragments[] array
     */
    private Fragment getCorrespondingActiveFragment(AbstractTransferDetailFragment currentFragment) {
        List<Fragment> fragments = getFragments();
        if (fragments.size() > 1) {
            for (Fragment f : fragments) {
                if (f.isAdded() && currentFragment.getClass() == f.getClass()) {
                    return f;
                }
            }
        }
        return null;
    }

    private void onTabSelected(int position) {
        lastSelectedTabIndex = position;
        ConfigurationManager.instance().setInt(Constants.PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX, lastSelectedTabIndex);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("lastSelectedTabIndex", lastSelectedTabIndex);
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
    }

    private static final class OnPageChangeListener implements ViewPager.OnPageChangeListener {
        private final TransferDetailActivity activity;

        OnPageChangeListener(TransferDetailActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            try {
                activity.onTabSelected(position);
                activity.onTime();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }
}
