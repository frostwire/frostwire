/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2007-2023, FrostWire(R). All rights reserved.
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

package com.frostwire.search.telluride;

import com.frostwire.concurrent.concurrent.ThreadExecutor;
import com.frostwire.util.Logger;

import java.io.*;

/**
 * A Java process builder and parser for the telluride cloud downloader.
 * Launches the process passing the appropriate options, parses its output to dispatch events to a TellurideListener
 */
public final class TellurideLauncher {

    private static final Logger LOG = Logger.getLogger(TellurideLauncher.class);

    /**
     * We're not using this method anymore, we now communicate with Telluride via HTTP
     * We're leaving this code for unit test purposes.
     *
     * @param metaOnly Get only metadata about the downloadUrl. If set to true, it ignores audioOnly
     */
    public static void launch(final File executable,
                              final String downloadUrl,
                              final File saveDirectory,
                              final boolean audioOnly,
                              final boolean metaOnly,
                              final boolean verboseOutput,
                              TellurideListener processListener) {
        checkExecutable(executable);
        ThreadExecutor.startThread(
                launchRunnable(
                        executable,
                        downloadUrl,
                        saveDirectory,
                        audioOnly,
                        metaOnly,
                        verboseOutput,
                        processListener),
                "telluride-process-adapter:" + downloadUrl);
    }

    private static void checkExecutable(final File executable) {
        if (executable == null) {
            throw new IllegalArgumentException("TellurideLauncher::checkExecutable executable path is null, no telluride to launch");
        }
        if (!executable.isFile()) {
            throw new IllegalArgumentException("TellurideLauncher::checkExecutable " + executable + " is not a file");
        }
        if (!executable.canExecute()) {
            throw new IllegalArgumentException("TellurideLauncher::checkExecutable " + executable + " is not executable");
        }
    }

    private static Runnable launchRunnable(final File executable,
                                           final String downloadUrl,
                                           final File saveDirectory,
                                           final boolean audioOnly,
                                           final boolean metaOnly,
                                           final boolean verboseOutput,
                                           TellurideListener processListener) {
        return () -> {
            ProcessBuilder processBuilder = new ProcessBuilder(executable.getAbsolutePath(), downloadUrl);
            processBuilder.redirectErrorStream(true); // merge stderr into stdout
            if (audioOnly) {
                processBuilder = new ProcessBuilder(
                        executable.getAbsolutePath(),
                        "-a",
                        downloadUrl);
            }
            if (metaOnly) {
                processBuilder = new ProcessBuilder(
                        executable.getAbsolutePath(),
                        "-m",
                        downloadUrl);
            }
            if (saveDirectory != null && saveDirectory.isDirectory() && saveDirectory.canWrite()) {
                processBuilder.directory(saveDirectory);
            }
            try {
                final Process process = processBuilder.start();
                final InputStream stdOut = process.getInputStream();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(stdOut));
                TellurideParser parser = null;
                if (processListener != null) {
                    parser = new TellurideParser(processListener, metaOnly);
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (verboseOutput) {
                        System.out.println("TellurideLauncher::launchRunnable [" + executable.getName() + "] " + line);
                    }
                    if (processListener != null && processListener.aborted()) {
                        process.destroyForcibly();
                        break;
                    }
                    if (parser != null) {
                        parser.parse(line);
                    }
                }
                process.waitFor();
                if (verboseOutput) {
                    System.out.println("TellurideLauncher::launchRunnable Exit-Code: " + process.exitValue());
                }
                if (parser != null) {
                    parser.done();
                }
                if (processListener != null) {
                    processListener.onFinished(process.exitValue());
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        };
    }

    private static class TelluridePong {
        int build;
        String message;
    }
}
