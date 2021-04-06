/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2007-2021, FrostWire(R). All rights reserved.
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
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Java process builder and parser for the telluride cloud downloader.
 * Launches the process passing the appropiate options, parses its output to dispatch events to a TellurideListener
 */
public final class TellurideLauncher {

    private static Logger LOG = Logger.getLogger(TellurideLauncher.class);

    public static AtomicBoolean SERVER_UP = new AtomicBoolean(false);

    public static void shutdownServer(final int port) {
        ThreadExecutor.startThread(() -> {
            HttpClient httpClient = HttpClientFactory.newInstance();
            try {
                httpClient.get(String.format("http://127.0.0.1:%d/?shutdown=1", port));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "TellurideLauncher::shutDownServer(" + port + ")");
    }

    public static void launchServer(final File executable,
                                    final int port,
                                    final File saveDirectory) {
        if (SERVER_UP.get()) {
            LOG.info("launchServer aborted, server already up.");
            return;
        }
        checkExecutable(executable);
        if (port < 8080) {
            throw new IllegalArgumentException("Please use a port greater or equal to 8080 (do not run frostwire as root). Telluride's default port number is 47999");
        }
        ThreadExecutor.startThread(() -> {
                    ProcessBuilder processBuilder = new ProcessBuilder(executable.getAbsolutePath(), "--server", String.valueOf(port));
                    processBuilder.redirectErrorStream(true);
                    if (saveDirectory != null && saveDirectory.isDirectory() && saveDirectory.canWrite()) {
                        processBuilder.directory(saveDirectory);
                    }
                    try {
                        processBuilder.start();
                        SERVER_UP.set(true);
                        LOG.info("RPC server is up successfully at http://127.0.0.1:" + port);
                    } catch (IOException e) {
                        SERVER_UP.set(false);
                        e.printStackTrace();
                    }
                },
                "telluride-server-on-port-" + port);
    }

    /**
     * @param executable
     * @param downloadUrl
     * @param saveDirectory
     * @param audioOnly
     * @param metaOnly        Get only metadata about the downloadUrl. If set to true, it ignores audioOnly
     * @param processListener
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
            throw new IllegalArgumentException("executable path is null, no telluride to launch");
        }
        if (!executable.isFile()) {
            throw new IllegalArgumentException(executable + " is not a file");
        }
        if (!executable.canExecute()) {
            throw new IllegalArgumentException(executable + " is not executable");
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
                        System.out.println("[" + executable.getName() + "] " + line);
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
                    System.out.println("Exit-Code: " + process.exitValue());
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
}
