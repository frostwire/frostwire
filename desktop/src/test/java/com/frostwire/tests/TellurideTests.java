/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2007-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.tests;

import com.frostwire.telluride.TellurideLauncher;
import com.frostwire.telluride.TellurideListener;
import org.junit.jupiter.api.Test;
import org.limewire.util.OSUtils;

import java.io.File;

public class TellurideTests {
    @Test
    public void testMetaOnly() throws InterruptedException {
        String executableSuffix = ".exe";
        if (OSUtils.isAnyMac()) {
            executableSuffix = "_macos";
        } else if (OSUtils.isLinux()) {
            executableSuffix = "_linux";
        }

        TellurideListener tellurideListener = new TellurideListener() {
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
            }

            @Override
            public void onError(String errorMessage) {
            }

            @Override
            public void onFinished(int exitCode) {
                System.out.println("[TellurideTests][testMetaOnly] got exitCode=" + exitCode);
                System.exit(exitCode); // TODO: change this as we have more tests.
            }

            @Override
            public void onDestination(String outputFilename) {
            }

            @Override
            public boolean aborted() {
                return false;
            }

            @Override
            public void onMeta(String json) {
                System.out.println("[TellurideTests][testMetaOnly] GOT JSON!");
                System.out.println(json);
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        TellurideLauncher.launch(new File("/Users/gubatron/workspace.frostwire/frostwire/telluride/telluride" + executableSuffix),
                "https://www.youtube.com/watch?v=1kaQP9XL6L4", // Alone Together - Mona Wonderlick · [Free Copyright-safe Music]
                new File("/Users/gubatron/FrostWire/Torrent Data"),
                false,
                true,
                tellurideListener);

        System.out.println("[TellurideTests][testMetaOnly] waiting...");
        Thread.currentThread().join();
        System.out.println("[TellurideTests][testMetaOnly] finished.");
    }
}
