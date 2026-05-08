/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.TaskStackBuilder;

import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.util.Logger;

public final class AudioPlayerNotificationActivity extends Activity {

    private static final Logger LOG = Logger.getLogger(AudioPlayerNotificationActivity.class);
    private static final int REQUEST_CODE = 0xA1D11;

    public static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, AudioPlayerNotificationActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, REQUEST_CODE, intent, flags);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOG.info("AudioPlayerNotificationActivity::onCreate opening player stack");
        Intent mainIntent = new Intent(this, MainActivity.class);
        Intent playerIntent = new Intent(this, AudioPlayerActivity.class);
        TaskStackBuilder.create(this)
                .addNextIntent(mainIntent)
                .addNextIntent(playerIntent)
                .startActivities();
        finish();
    }
}
