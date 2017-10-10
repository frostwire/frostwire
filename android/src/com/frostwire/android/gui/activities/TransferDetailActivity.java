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
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractFragment;

import java.util.HashMap;

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


    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        private final HashMap<Integer, AbstractFragment> detailFragments;
        private final String[] titles;

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            detailFragments = new HashMap<>();
            titles = initPageTitles();
        }

        @Override
        public AbstractFragment getItem(int position) {
            switch(position) {
                case 0:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_details)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_details, new TransferDetailDetailsFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_details);
                case 1:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_status)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_status, new TransferDetailStatusFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_status);
                case 2:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_files)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_files, new TransferDetailFilesFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_files);
                case 3:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_trackers)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_trackers, new  TransferDetailTrackersFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_trackers);
                case 4:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_peers)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_peers, new TransferDetailPeersFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_peers);
                case 5:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_pieces)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_pieces, new TransferDetailPiecesFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_pieces);
                default:
                    if (!detailFragments.containsKey(R.layout.fragment_transfer_detail_status)) {
                        detailFragments.put(R.layout.fragment_transfer_detail_status, new TransferDetailStatusFragment());
                    }
                    return detailFragments.get(R.layout.fragment_transfer_detail_status);
            }
        }

        @Override
        public int getCount() {
            return titles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }

        private String[] initPageTitles() {
            String[] pageTitles = new String[] {
                    getString(R.string.details),  // 0
                    getString(R.string.status),   // 1
                    getString(R.string.trackers), // 2
                    getString(R.string.peers),    // 3
                    getString(R.string.pieces),   // 4
                    getString(R.string.status),   // 5
            };
            for (int i=0; i < pageTitles.length; i++) {
                pageTitles[i] = pageTitles[i].toUpperCase();
            }
            return pageTitles;
        }
    }
}
