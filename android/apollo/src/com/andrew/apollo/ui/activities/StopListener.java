/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
class StopListener implements View.OnLongClickListener {
    private WeakReference<Activity> activityRef;
    private final boolean finishOnStop;

    StopListener(Activity activity, boolean finishOnStop) {
        this.activityRef = Ref.weak(activity);
        this.finishOnStop = finishOnStop;
    }

    @Override
    public boolean onLongClick(View v) {
        async(v, StopListener::stopMusicTask);
        if (Ref.alive(activityRef)) {
            if (finishOnStop) {
                activityRef.get().onBackPressed();
            }
        } else {
            Ref.free(activityRef);
        }
        return true;
    }

    private static void stopMusicTask(View v) {
        try {
            MusicPlaybackService musicPlaybackService = MusicUtils.getMusicPlaybackService();
            if (musicPlaybackService != null) {
                musicPlaybackService.stop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        v.getContext().sendBroadcast(new Intent(Constants.ACTION_MEDIA_PLAYER_STOPPED));
    }
}
