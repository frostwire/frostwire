/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package io.presage.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import io.presage.Presage;

import static com.frostwire.android.util.Asyncs.async;
import static com.frostwire.android.util.Debug.runStrict;

/**
 * This class is part of a hack to avoid the ANR that happens
 * with such a high frequency in the original AlarmReceiver.
 * <p>
 * The jar used is a modified one with just this class removed
 * from it and recreated here.
 * <p>
 * TODO: this needs to be solved with the third party provider
 */
public final class AlarmReceiver extends BroadcastReceiver {

    // empty constructor to be able to place a breakpoint
    public AlarmReceiver() {
    }

    public void onReceive(Context context, Intent intent) {
        runStrict(() -> onReceiveSafe(context, intent));
    }

    private void onReceiveSafe(Context context, Intent intent) {
        try {
            async(context, (ctx) -> {
                try {
                    Presage.getInstance().setContext(ctx);
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        Presage.getInstance().talkToService((Bundle) extras.clone());
                    }
                } catch (Throwable e) {
                    // just log
                    e.printStackTrace();
                }
            });
        } catch (Throwable e) {
            // just log
            e.printStackTrace();
        }
    }
}
