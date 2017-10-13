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
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;

import java.util.List;

public class TransferDetailActivity extends AbstractActivity {

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String infoHash = getIntent().getStringExtra("infoHash");

        if (infoHash == null || infoHash == "") {
            throw new RuntimeException("Invalid infoHash received");
        }

        UIBittorrentDownload uiBittorrentDownload = (UIBittorrentDownload)
                TransferManager.instance().getBittorrentDownload(infoHash);

        if (uiBittorrentDownload == null) {
            throw new RuntimeException("Could not find matching transfer for infoHash:"+infoHash);
        }

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager(), uiBittorrentDownload);

        ViewPager mViewPager = findViewById(R.id.transfer_detail_viewpager);
        if (mViewPager != null) {
            mViewPager.setAdapter(mSectionsPagerAdapter);
            // TODO: remember last selected
            mViewPager.setCurrentItem(0);

            TabLayout tabLayout = findViewById(R.id.transfer_detail_tab_layout);
            tabLayout.setupWithViewPager(mViewPager);
        }
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        private final AbstractTransferDetailFragment[] detailFragments;

        SectionsPagerAdapter(FragmentManager fm, UIBittorrentDownload uiBittorrentDownload) {
            super(fm);
            detailFragments = initFragments(uiBittorrentDownload);
        }

        private AbstractTransferDetailFragment[] initFragments(UIBittorrentDownload uiBittorrentDownload) {
            // to change the order of the tabs, add/remove tabs, just maintain here.
            AbstractTransferDetailFragment[] fragments = {
                    new TransferDetailStatusFragment().init(getString(R.string.status), uiBittorrentDownload),
                    new TransferDetailFilesFragment().init(getString(R.string.files), uiBittorrentDownload),
                    new TransferDetailDetailsFragment().init(getString(R.string.details), uiBittorrentDownload),
                    new TransferDetailTrackersFragment().init(getString(R.string.trackers), uiBittorrentDownload),
                    new TransferDetailPeersFragment().init(getString(R.string.peers), uiBittorrentDownload),
                    new TransferDetailPiecesFragment().init(getString(R.string.pieces), uiBittorrentDownload)
            };
            return fragments;
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
}
