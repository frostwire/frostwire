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

import android.content.Context;
import android.content.Intent;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

import static com.frostwire.android.util.Debug.runStrict;

/**
 * This class is part of a hack to avoid the ANR that happens
 * with such a high frequency in the original NetworkChangeReceiver.
 * <p>
 * TODO: this needs to be solved with the third party provider
 */
public final class NetworkChangeReceiver2 extends NetworkChangeReceiver {

    // empty constructor to be able to place a breakpoint
    public NetworkChangeReceiver2() {
    }

    public void onReceive(Context context, Intent intent) {
        runStrict(() -> onReceiveSafe(context, intent));
    }

    private void onReceiveSafe(Context context, Intent intent) {
        try {
            final WeakReference<Context> ctxRef = Ref.weak(context);
            // run the task in the background to avoid ANR
            Engine.instance().getThreadPool().execute(() -> {
                try {
                    if (Ref.alive(ctxRef)) {
                        NetworkChangeReceiver2.super.onReceive(ctxRef.get(), intent);
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
