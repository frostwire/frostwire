/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2020, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.activities;

import android.app.Fragment;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import com.andrew.apollo.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.offers.Offers;

/**
 * This class is used to display the {@link ViewPager} used to swipe between the
 * main {@link Fragment}s used to browse the user's music.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class HomeActivity extends BaseActivity {

    private DangerousPermissionsChecker dangerousPermissionsChecker;

    public HomeActivity() {
        super(R.layout.activity_base);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the music browser fragment
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.activity_base_content, new MusicBrowserPhoneFragment()).commit();
        }

        dangerousPermissionsChecker = new DangerousPermissionsChecker(this,
                DangerousPermissionsChecker.READ_EXTERNAL_STORAGE);
        dangerousPermissionsChecker.requestPermissions();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (MusicUtils.isPlaying()) {
            return;
        }
        Offers.showInterstitialOfferIfNecessary(
                this,
                Offers.PLACEMENT_INTERSTITIAL_MAIN,
                false,
                false,
                true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        dangerousPermissionsChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
