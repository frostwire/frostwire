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

package com.limegroup.gnutella.gui.search.adapters;

import com.frostwire.search.SearchPerformer;
import com.frostwire.search.TellurideAdapter;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.limegroup.gnutella.gui.search.TellurideSearchPerformerDesktopListener;
import com.limegroup.gnutella.util.FrostWireUtils;

/**
 * Desktop implementation of TellurideAdapter that uses
 * TellurideSearchPerformer with HTTP server integration.
 * 
 * @author gubatron
 * @author aldenml
 */
public class DesktopTellurideAdapter implements TellurideAdapter {
    
    @Override
    public SearchPerformer createTelluridePerformer(long token, String keywords) {
        return new TellurideSearchPerformer(token,
                keywords,
                new TellurideSearchPerformerDesktopListener(),
                FrostWireUtils.getTellurideLauncherFile());
    }
    
    @Override
    public Object createTelluridePerformer(long token, String pageUrl, Object adapter) {
        // Desktop doesn't use the page URL + adapter pattern
        // Instead it uses the keyword-based approach
        return createTelluridePerformer(token, pageUrl);
    }
    
    @Override
    public boolean isTellurideSupported() {
        return FrostWireUtils.getTellurideLauncherFile() != null && 
               FrostWireUtils.getTellurideLauncherFile().exists();
    }
}