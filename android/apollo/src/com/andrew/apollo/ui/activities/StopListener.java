/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public class StopListener implements View.OnLongClickListener {
    private WeakReference<Activity> activityRef;
    private final boolean finishOnStop;

    public StopListener(Activity activity, boolean finishOnStop) {
        this.activityRef = Ref.weak(activity);
        this.finishOnStop = finishOnStop;
    }

    @Override
    public boolean onLongClick(View v) {
        try {
            MusicUtils.mService.stop();
            if (Ref.alive(activityRef)) {
                v.getContext().sendBroadcast(new Intent(Constants.ACTION_MEDIA_PLAYER_STOPPED));

                if (finishOnStop) {
                    activityRef.get().onBackPressed();
                }
            }
        } catch (Throwable e) {
            // ignore, just print
            e.printStackTrace();
        }

        return true;
    }
}
