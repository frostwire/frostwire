/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.activities;


import android.os.Bundle;
import android.util.SparseArray;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

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
import com.frostwire.android.offers.HeaderBanner;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.transfers.BittorrentDownload;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

public class TransferDetailActivity extends AbstractActivity implements TimerObserver {
    private static final int TRANSFER_DETAIL_ACTIVITY_TIMER_INTERVAL_IN_SECS = 2;
    private static TimerSubscription subscription;
    private UIBittorrentDownload uiBittorrentDownload;
    private SparseArray<String> tabTitles;
    private AbstractTransferDetailFragment[] detailFragments;
    private TransferDetailFragment transferDetailFragment;
    private int lastSelectedTabIndex;
    private HeaderBanner headerBanner;

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
        headerBanner = findView(R.id.transfer_detail_header_banner);
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
                new SectionsPagerAdapter(getSupportFragmentManager(), detailFragments);
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
            BittorrentDownload bittorrentDownload = TransferManager.instance().getBittorrentDownload(infoHash);
            if (bittorrentDownload instanceof UIBittorrentDownload) {
                uiBittorrentDownload = (UIBittorrentDownload) bittorrentDownload;
            } else if (bittorrentDownload instanceof BTDownload) {
                uiBittorrentDownload = new UIBittorrentDownload(TransferManager.instance(), (BTDownload) bittorrentDownload);
            }
        }
        if (uiBittorrentDownload != null) {
            uiBittorrentDownload.checkSequentialDownload();
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
        if (uiBittorrentDownload == null) {
            throw new RuntimeException("No UIBittorrent download, unacceptable");
        }
        HeaderBanner.onResumeHideOrUpdate(headerBanner);
        if (subscription != null) {
            if (subscription.isSubscribed()) {
                subscription.unsubscribe();
            }
            TimerService.reSubscribe(this, subscription, TRANSFER_DETAIL_ACTIVITY_TIMER_INTERVAL_IN_SECS);
        } else {
            subscription = TimerService.subscribe(this, TRANSFER_DETAIL_ACTIVITY_TIMER_INTERVAL_IN_SECS);
        }
        onTime();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        HeaderBanner.destroy(headerBanner);
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
