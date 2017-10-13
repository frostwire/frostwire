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
import android.content.Context;
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
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;

public class TransferDetailActivity extends AbstractActivity {

    public TransferDetailActivity() {
        super(R.layout.activity_transfer_detail);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(this, getFragmentManager());

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

        SectionsPagerAdapter(Context context, FragmentManager fm) {
            super(fm);
            detailFragments = initFragments(context);
        }

        private AbstractTransferDetailFragment[] initFragments(Context context) {
            // to change the order of the tabs, add/revmove tabs, just maintain here.
            AbstractTransferDetailFragment[] fragments = {
                    new TransferDetailStatusFragment(),
                    new TransferDetailFilesFragment(),
                    new TransferDetailDetailsFragment(),
                    new TransferDetailTrackersFragment(),
                    new TransferDetailPeersFragment(),
                    new TransferDetailPiecesFragment()
            };
            String[] tabTitles = {
                    context.getString(R.string.status),
                    context.getString(R.string.files),
                    context.getString(R.string.details),
                    context.getString(R.string.trackers),
                    context.getString(R.string.peers),
                    context.getString(R.string.pieces)
            };
            for (int i=0; i < fragments.length; i++) {
                Bundle bundle = new Bundle();
                bundle.putString("tabTitle", tabTitles[i]);
                fragments[i].setArguments(bundle);
            }
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
            return detailFragments[position].getTitle().toUpperCase();
        }
    }
}
