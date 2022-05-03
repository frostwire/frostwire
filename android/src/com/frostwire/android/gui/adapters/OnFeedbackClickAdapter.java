/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.frostwire.android.gui.adapters;

import android.app.Fragment;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.RichNotification;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Created on 9/29/16 in Denver, CO.
 * Opens default email client and pre-fills email to support@frostwire.com
 * with some information about the app and environment.
 *
 * @author gubatron
 * @author aldenml
 */

public class OnFeedbackClickAdapter extends ClickAdapter<Fragment> {
    private final WeakReference<RichNotification> ratingReminderRef;
    private final ConfigurationManager CM;

    public OnFeedbackClickAdapter(Fragment owner, final RichNotification ratingReminder, final ConfigurationManager CM) {
        super(owner);
        ratingReminderRef = ratingReminder != null ? Ref.weak(ratingReminder) : null;
        this.CM = CM;
    }

    @Override
    public void onClick(Fragment owner, View v) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@frostwire.com"});
        String plusOrBasic = (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) ? "basic" : "plus";
        intent.putExtra(Intent.EXTRA_SUBJECT, String.format("[Feedback - frostwire-android (%s) - v%s b%s]", plusOrBasic, Constants.FROSTWIRE_VERSION_STRING, Constants.FROSTWIRE_BUILD));

        String body = String.format(Locale.US, "FrostWire for Android %s build %s %s \n\nAndroid SDK: %d\nAndroid RELEASE: %s (%s)\nManufacturer-Model: %s - %s\nDevice: %s\nBoard: %s\nCPU ABI: %s\nCPU ABI2: %s\n\n\n\n",
                Constants.FROSTWIRE_VERSION_STRING,
                Constants.FROSTWIRE_BUILD,
                Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? "" : "(PLUS)",
                Build.VERSION.SDK_INT,
                Build.VERSION.RELEASE,
                Build.VERSION.CODENAME,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.DEVICE,
                Build.BOARD,
                Build.CPU_ABI,
                Build.CPU_ABI2);

        intent.putExtra(Intent.EXTRA_TEXT, body);
        owner.startActivity(Intent.createChooser(intent, owner.getString(R.string.choose_email_app)));

        if (Ref.alive(ratingReminderRef)) {
            ratingReminderRef.get().setVisibility(View.GONE);
        }
        CM.setBoolean(Constants.PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET, true);
    }
}