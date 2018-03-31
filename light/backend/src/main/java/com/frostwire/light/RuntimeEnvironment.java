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

package com.frostwire.light;

import com.frostwire.light.util.OSUtils;

import java.io.File;
import java.util.Map;

public final class RuntimeEnvironment {
    final RuntimeMode mode;
    final File backendFolder;
    final File frontendFolder;
    final boolean isWindows;
    final boolean isMacOSX;
    final boolean isLinux;
    final String os;
    final String osVersion;
    final String osArchitecture;
    final String fullOSString;

    enum RuntimeMode {
        DEVELOPMENT,
        PRODUCTION
    }

    private RuntimeEnvironment(RuntimeMode mode, File backendFolder, File frontendFolder) {
        if (backendFolder == null) {
            throw new RuntimeException("RuntimeEnvironment backendFolder is null, check your logic.");
        }
        if (frontendFolder == null) {
            throw new RuntimeException("RuntimeEnvironment frontendFOlder is null, check your logic.");
        }
        this.mode = mode;
        this.backendFolder = backendFolder;
        this.frontendFolder = frontendFolder;

        // OS Properties
        this.isWindows = OSUtils.isWindows();
        this.isMacOSX = OSUtils.isMacOSX();
        this.isLinux = OSUtils.isLinux();
        this.os = OSUtils.getOS();
        this.osVersion = OSUtils.getOSVersion();
        this.osArchitecture = OSUtils.getArchitecture();
        this.fullOSString = OSUtils.getFullOS();
    }

    public static RuntimeEnvironment init() {
        File pwd = new File(".");
        String[] list = pwd.list();

        boolean foundBuildGradle = false;
        for (String fileName : list) {
            if ("build.gradle".equals(fileName)) {
                foundBuildGradle = true;
                break;
            }
        }

        RuntimeMode mode = foundBuildGradle ? RuntimeMode.DEVELOPMENT : RuntimeMode.PRODUCTION;
        File backendDir = null;
        File frontendDir = null;

        if (mode == RuntimeMode.DEVELOPMENT) {
            backendDir = pwd;
            frontendDir = new File(pwd.getAbsolutePath() + File.separator + ".." + File.separator + "frontend" + File.separator + "src");
        } else if (mode == RuntimeMode.PRODUCTION) {
            // TODO: figure out paths when we have a production binary
        }
        return new RuntimeEnvironment(mode, backendDir, frontendDir);
    }
}
