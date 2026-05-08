/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.TaskStackBuilder;

import com.frostwire.android.gui.activities.MainActivity;

public final class AudioPlayerNotificationActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent mainIntent = new Intent(this, MainActivity.class);
        Intent playerIntent = new Intent(this, AudioPlayerActivity.class);
        TaskStackBuilder.create(this)
                .addNextIntent(mainIntent)
                .addNextIntent(playerIntent)
                .startActivities();
        finish();
    }
}
