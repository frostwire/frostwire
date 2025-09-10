/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.adapters;

import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.TellurideAdapter;

/**
 * Android implementation of TellurideAdapter that uses
 * TellurideCourier for Telluride integration.
 * 
 * @author gubatron
 * @author aldenml
 */
public class AndroidTellurideAdapter implements TellurideAdapter {
    
    @Override
    public SearchPerformer createTelluridePerformer(long token, String keywords) {
        // Android doesn't support keyword-based Telluride search directly
        return null;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Object createTelluridePerformer(long token, String pageUrl, Object adapter) {
        if (adapter instanceof AbstractListAdapter) {
            return new TellurideCourier.SearchPerformer<>(token, pageUrl, (AbstractListAdapter) adapter);
        }
        return null;
    }
    
    @Override
    public boolean isTellurideSupported() {
        return true;
    }
}