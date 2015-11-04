/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.appwidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.andrew.apollo.MusicPlaybackService;

public abstract class AppWidgetBase extends AppWidgetProvider {

    protected PendingIntent buildPendingIntent(Context context, final String action,
            final ComponentName serviceName) {
        Intent intent = new Intent(action);
        intent.setComponent(serviceName);
        intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, false);
        return PendingIntent.getService(context, 0, intent, 0);
    }

}
