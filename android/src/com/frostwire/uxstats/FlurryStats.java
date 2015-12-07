/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

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

package com.frostwire.uxstats;

import android.content.Context;
import android.util.SparseArray;
import com.flurry.android.FlurryAgent;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class FlurryStats implements UXStats3rdPartyAPI {
    private static final String frostwireBasicKey = "8VNT59GNV587SVK6MXWK";
    private static final String frostwirePlusKey = "29W5CWQZF58S8394R27P";

    private final String key;
    private final SparseArray<String> actions;
    private final WeakReference<Context> contextRef;

    public FlurryStats(Context context) {
        key = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? frostwireBasicKey : frostwirePlusKey;
        actions = new SparseArray<>(86);
        contextRef = Ref.weak(context);
        FlurryAgent.setLogEnabled(false);
        FlurryAgent.init(context, key);
        initActionCodeMap();
    }

    /**
     * uses reflection to generate the same list of action codes we define on UXAction, but we'll use their names,
     * Flurry's events are string codes, which we'll keep on a sparse array for quick integeration
     * based on int action codes passed to UXStats.logAction().
     */
    private void initActionCodeMap() {
        Field[] fields = UXAction.class.getDeclaredFields();

        for (Field f : fields) {
            final int modifiers = f.getModifiers();
            if (Modifier.isPublic(modifiers) &&
                Modifier.isStatic(modifiers) &&
                Modifier.isFinal(modifiers) &&
                f.getType() == int.class) {
                try {
                    actions.append(f.getInt(null), f.getName());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void logAction(int action) {
        String actionName = actions.get(action);
        if (actionName != null) {
            FlurryAgent.logEvent(actionName);
        }
    }

    @Override
    public void endSession() {
        if (Ref.alive(contextRef)) {
            FlurryAgent.onEndSession(contextRef.get());
        }
    }
}
