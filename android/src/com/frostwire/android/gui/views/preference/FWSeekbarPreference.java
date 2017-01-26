/*
 * Copyright (C) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;

import com.frostwire.android.R;

import java.lang.reflect.Field;

/**
 * Support version of a custom dialog preference
 *
 * @author grzesiekrzaca
 * @author gubatron
 */
public final class FWSeekbarPreference extends DialogPreference {

    private final int startRange;
    private final int endRange;
    private final int defaultValue;
    private final boolean isByteRate;
    private final int pluralUnitResourceId;
    private final boolean hasUnlimited;
    private final int unlimitedValue;

    public FWSeekbarPreference(Context context, AttributeSet attrs) {

        super(context, attrs);
        setDialogLayoutResource(R.layout.dialog_preference_seekbar_with_checkbox);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.fwSeekbarPreference);
        startRange = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_startRange, 0);
        endRange = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_endRange, 100);
        defaultValue = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_defaultValue, 0);
        isByteRate = arr.getBoolean(R.styleable.fwSeekbarPreference_seekbar_isByteRate, false);
        hasUnlimited = arr.getBoolean(R.styleable.fwSeekbarPreference_seekbar_hasUnlimited, false);
        unlimitedValue = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_unlimitedValue, 0);
        pluralUnitResourceId = getPluralUnitResourceId(arr);
        arr.recycle();
    }

    /**
     * ANDROID API EDGE FOUND: @plurals/my_plural is not supported in XML layouts
     * supposedly because of translation support issues, I don't see why, as you coul
     * have multiple plurals.xml defined, one per each language.
     * For now, we just put the name of the plural field in the XML layout and we
     * use reflection to fetch it from the R.java file.
     *
     * See:
     *   - plurals.xml
     *   - attrs.xml (fwSeekbarPreference::seekbar_pluralUnitResourceIdName)
     *   - settings_torrents.xml
     *   - frostwire.prefs.torrent.max_downloads and others below which use units (non byte rates)
     */
    private int getPluralUnitResourceId(TypedArray arr) {
        String pluralUnitResourceIdName = arr.getString(R.styleable.fwSeekbarPreference_seekbar_pluralUnitResourceIdName);
        int pluralUnitResourceIdTemp = -1;
        if (pluralUnitResourceIdName != null) {
            try {
                Class<?> pluralsClass = Class.forName("com.frostwire.android.R$plurals");
                Field declaredField = pluralsClass.getDeclaredField(pluralUnitResourceIdName);
                pluralUnitResourceIdTemp = declaredField.getInt(null);
            } catch (Throwable t) {
                t.printStackTrace();
                pluralUnitResourceIdTemp = -1;
            }
        }
        return pluralUnitResourceIdTemp;
    }

    public void saveValue(long val) {
        persistLong(val);
        notifyChanged();
    }

    public int getStartRange() {
        return startRange;
    }

    public int getEndRange() {
        return endRange;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public boolean isByteRate() {
        return isByteRate;
    }

    public int getPluralUnitResourceId() {
        return pluralUnitResourceId;
    }

    public boolean supportsUnlimitedValue() {
        return hasUnlimited;
    }

    public int getUnlimitedValue() {
        return unlimitedValue;
    }
}
