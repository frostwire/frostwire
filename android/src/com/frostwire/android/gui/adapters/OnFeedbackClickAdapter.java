/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

        String body = String.format("FrostWire for Android %s build %s %s \n\nAndroid SDK: %d\nAndroid RELEASE: %s (%s)\nManufacturer-Model: %s - %s\nDevice: %s\nBoard: %s\nCPU ABI: %s\nCPU ABI2: %s\n\n\n\n",
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