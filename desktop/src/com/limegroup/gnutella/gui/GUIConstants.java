/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.settings.UISettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.File;

/**
 * Constants used by gui classes.
 */
public final class GUIConstants {
    /**
     * Standard number of pixels that should separate many
     * different types of gui components.
     */
    public static final int SEPARATOR = 6;
    public static final String TWITTER_FROSTWIRE_URL = "https://twitter.com/frostwire";
    public static final String FACEBOOK_FROSTWIRE_URL = "https://www.facebook.com/FrostwireOfficial";
    public static final String REDDIT_FROSTWIRE_URL = "https://www.reddit.com/r/frostwire";
    public static final String FROSTWIRE_CHAT_URL = "https://www.frostwire.com/chat";

    static final String FROSTWIRE_64x64_ICON = "frostwire64x64";
    /**
     * Constant for the path to the LimeWire Windows launcher.
     */
    static final File FROSTWIRE_EXE_FILE = new File("FrostWire.exe").getAbsoluteFile();
    /**
     * The number of pixels in the margin of a padded panel.
     */
    static final int OUTER_MARGIN = 6;
    static final String INSTAGRAM_FROSTWIRE_URL = "https://instagram.com/frostwire";
    // Continuous Integration

    enum State {
        ALPHA, // alpha features are DISABLED by default in Experimental settings. User can enable ALPHA features.
        BETA   // beta features are ENABLED by default in Experimental settings. User can disable BETA features.
    }
    // alpha and beta features are always enabled when running from source.

    public enum Feature {
        VPN_DROP_GUARD(State.BETA);
        private final State status;

        Feature(State status) {
            this.status = status;
        }

        public boolean enabled() {
            // All features are enabled if running from source.
            boolean enabled =
                    (status == State.ALPHA && UISettings.ALPHA_FEATURES_ENABLED.getValue()) ||
                            (status == State.BETA && UISettings.BETA_FEATURES_ENABLED.getValue()) ||
                            (FrostWireUtils.isIsRunningFromSource());
            System.out.println("INFO: " + status.name() + " Feature." + this.name() + " enabled: " + enabled);
            return enabled;
        }
    }
}
