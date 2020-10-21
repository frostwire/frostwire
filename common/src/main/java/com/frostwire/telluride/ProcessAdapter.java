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

package com.frostwire.telluride;

import org.limewire.util.OSUtils;

import java.io.*;

public class ProcessAdapter {
    interface ProcessListener {
        // [download]  30.5% of 277.93MiB at 507.50KiB/s ETA 06:29
        void onProgress(float completionPercentage,
                        float fileSize,
                        String fileSizeUnits,
                        float downloadSpeed,
                        String downloadSpeedUnits,
                        String ETA);

        void onError(String errorMessage);

        void onFinished();

        void onDestination(String outputFilename);

        boolean shouldCancel();
    }

    public static void launch(final File executable,
                              final String downloadUrl,
                              final File saveDirectory,
                              final boolean audioOnly,
                              ProcessListener processListener) {
        if (executable == null) {
            throw new IllegalArgumentException("executable path is null, no telluride to launch");
        }
        if (!executable.isFile()) {
            throw new IllegalArgumentException(executable + " is not a file");
        }
        if (!executable.canExecute()) {
            throw new IllegalArgumentException(executable + " is not executable");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(executable.getAbsolutePath(), downloadUrl);

        if (audioOnly) {
            processBuilder = new ProcessBuilder(
                    executable.getAbsolutePath(),
                    "-a",
                    downloadUrl);
        }

        try {
            if (saveDirectory != null && saveDirectory.isDirectory() && saveDirectory.canWrite()) {
                processBuilder.directory(saveDirectory);
            }
            final Process process = processBuilder.start();
            final InputStream stdOut = process.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stdOut));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + executable.getName() + "] " + line);

                if (processListener !=null&& processListener.shouldCancel()) {
                    process.destroyForcibly();
                    break;
                }
            }
            process.waitFor();
            System.out.println("Exit-Code: " + process.exitValue());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String executableSuffix = ".exe";
        if (OSUtils.isAnyMac()) {
            executableSuffix = "_macos";
        } else if (OSUtils.isLinux()) {
            executableSuffix = "_linux";
        }
        launch(new File("/Users/gubatron/workspace.frostwire/frostwire/telluride/telluride" + executableSuffix),
                "https://www.youtube.com/watch?v=1kaQP9XL6L4",
                new File("/Users/gubatron/Desktop"),
                false,
                null);
    }
}
