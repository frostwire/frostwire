/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.tests;

import com.frostwire.search.telluride.TellurideLauncher;
import com.frostwire.search.telluride.TellurideListener;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Set FW_WORKSPACE_PATH=<path to the folder containing the "frostwire" repository folder>
 * otherwise it will assume your workspace folder is ${HOME}/workspace
 * */
public class TellurideTests {
    static String workspacePath = System.getenv("FW_WORKSPACE_PATH") != null ? System.getenv("FW_WORKSPACE_PATH") : System.getenv("HOME") + "/workspace";

    static {
        File data = new File(System.getenv("HOME") + "/FrostWire/Torrent Data", "Video_by_gubatron-CDC5ludJazw.mp4");
        if (data.exists()) {
            //noinspection ResultOfMethodCallIgnored
            data.delete();
        }
    }

    static boolean progressWasReported = false;
    private static final Logger LOG = Logger.getLogger(TellurideTests.class);

    @Test
    public void testConnectionError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> failedTests = new ArrayList<>();
        TellurideListener tellurideListener = new TellurideListener() {
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
                LOG.info("[TellurideTests][testConnectionError] onProgress(completionPercentage=" +
                        completionPercentage + ", fileSize=" +
                        fileSize + ", fileSizeUnits=" +
                        fileSizeUnits + ", downloadSpeed=" +
                        downloadSpeed + ", downloadSpeedUnits=" +
                        downloadSpeedUnits + ", ETA=" + ETA + ")");
                progressWasReported = true;
            }

            @Override
            public void onError(String errorMessage) {
                LOG.info("[TellurideTests][testConnectionError] (expected) onError(errorMessage=" + errorMessage + ")");
            }

            @Override
            public void onFinished(int exitCode) {
                if (exitCode == 0) {
                    failedTests.add("[TellurideTests][testConnectionError] onFinished(exitCode=" + exitCode + ") should've been an error code");
                }
                if (progressWasReported) {
                    failedTests.add("[TellurideTests][testConnectionError] Failed, did receive onProgress call. onFinished(exitCode=" + exitCode + ")");
                }
                latch.countDown();
            }

            @Override
            public void onDestination(String outputFilename) {
                LOG.info("[TellurideTests][testConnectionError] onDestination(outputFilename=" + outputFilename + ")");
            }

            @Override
            public boolean aborted() {
                return false;
            }

            @Override
            public void onMeta(String json) {
                LOG.info("[TellurideTests][testConnectionError] GOT JSON!");
                LOG.info(json);
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        File tellurideLauncherFile = FrostWireUtils.getTellurideLauncherFile();

        if (!tellurideLauncherFile.exists()) {
            LOG.warn("Aborting Telluride tests, telluride launcher not found in this environment");
            return;
        }

        TellurideLauncher.launch(tellurideLauncherFile,
                "https://www.youtube.com/watch?v=fail",
                new File(System.getenv("HOME") + "/FrostWire/Torrent Data"),
                false,
                false,
                true,
                tellurideListener);

        LOG.info("[TellurideTests][testConnectionError] waiting...");
        latch.await();
        if (failedTests.size() > 0) {
            fail("[TellurideTests][testConnectionError] isn't failing as expected ;)");
        }
        LOG.info("[TellurideTests][testConnectionError] finished.");
    }

    @Test
    public void testDownload() throws InterruptedException {
        progressWasReported = false;
        CountDownLatch latch = new CountDownLatch(1);
        List<String> failedTests = new ArrayList<>();
        TellurideListener tellurideListener = new TellurideListener() {
            private String fileName;
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
                LOG.info("[TellurideTests][testDownload] onProgress(completionPercentage=" +
                        completionPercentage + ", fileSize=" +
                        fileSize + ", fileSizeUnits=" +
                        fileSizeUnits + ", downloadSpeed=" +
                        downloadSpeed + ", downloadSpeedUnits=" +
                        downloadSpeedUnits + ", ETA=" + ETA + ")");
                progressWasReported = true;
            }

            @Override
            public void onError(String errorMessage) {
                failedTests.add("[TellurideTests][testDownload] onError(errorMessage=" + errorMessage + ")");
            }

            @Override
            public void onFinished(int exitCode) {
                if (exitCode != 0) {
                    failedTests.add("[TellurideTests][testDownload] onFinished(exitCode=" + exitCode + ")");
                }
                LOG.info("[TellurideTests][testDownload] onFinished: current working directory is " + System.getProperty("user.dir"));
                if (fileName != null) {
                    File file = new File(fileName);
                    if (file.exists()) {
                        LOG.info("[TellurideTests][testDownload] deleting file: " + fileName);
                        file.delete();
                    }
                } else {
                    failedTests.add("[TellurideTests][testDownload] Failed, fileName is null");
                }
                if (!progressWasReported) {
                    failedTests.add("[TellurideTests][testDownload] Failed, did not receive onProgress call. onFinished(exitCode=" + exitCode + ")");
                }
                latch.countDown();
            }

            @Override
            public void onDestination(String outputFilename) {
                LOG.info("[TellurideTests][testDownload] onDestination(outputFilename=" + outputFilename + ")");
                fileName = outputFilename;
            }

            @Override
            public boolean aborted() {
                return false;
            }

            @Override
            public void onMeta(String json) {
                LOG.info("[TellurideTests][testDownload] GOT JSON!");
                LOG.info(json);
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        File tellurideLauncherFile = FrostWireUtils.getTellurideLauncherFile();

        if (!tellurideLauncherFile.exists()) {
            LOG.warn("Aborting Telluride tests, telluride launcher not found in this environment");
            return;
        }

        TellurideLauncher.launch(tellurideLauncherFile,
                "https://www.youtube.com/watch?v=ye2CLllRO8I",
                new File(System.getenv("HOME") + "/FrostWire/Torrent Data"),
                false,
                false,
                true,
                tellurideListener);

        LOG.info("[TellurideTests][testDownload] waiting...");
        latch.await();
        if (failedTests.size() > 0) {
            fail(failedTests.get(0));
        }
        LOG.info("[TellurideTests][testDownload] finished.");
    }

    @Test
    public void testMetaOnly() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> failedTests = new ArrayList<>();
        TellurideListener tellurideListener = new TellurideListener() {
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
            }

            @Override
            public void onError(String errorMessage) {
                failedTests.add("[TellurideTests][testMetaOnly] onError(errorMessage=" + errorMessage + ")");
            }

            @Override
            public void onFinished(int exitCode) {
                if (exitCode != 0) {
                    failedTests.add("[TellurideTests][testMetaOnly] onFinished(exitCode=" + exitCode + ")");
                }
                latch.countDown();
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
                LOG.info("[TellurideTests][testMetaOnly] GOT JSON!");
                LOG.info(json);
                Gson gson = new GsonBuilder().create();
                List<TellurideSearchResult> validResults = TellurideSearchPerformer.getValidResults(json, gson, null, -1, "https://www.youtube.com/watch?v=RWVBoK4idas");
                if (validResults.size() == 0) {
                    failedTests.add("[TellurideTests][testMetaOnly] onMeta(json=" + json + ") no valid results found.");
                }
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        File tellurideLauncherFile = FrostWireUtils.getTellurideLauncherFile();

        if (!tellurideLauncherFile.exists()) {
            LOG.warn("Aborting Telluride tests, telluride launcher not found in this environment");
            return;
        }

        TellurideLauncher.launch(tellurideLauncherFile,
                "https://www.youtube.com/watch?v=1kaQP9XL6L4", // "Is FrostWire Legal?" youtube video
                new File(System.getenv("HOME")+ "/FrostWire/Torrent Data"),
                false,
                true,
                true,
                tellurideListener);

        LOG.info("[TellurideTests][testMetaOnly] waiting...");
        latch.await();
        if (failedTests.size() > 0) {
            fail(failedTests.get(0));
        }
        LOG.info("[TellurideTests][testMetaOnly] finished.");
    }

    @Test
    public void testGettingLauncherFile() {
        File launcherBinary = FrostWireUtils.getTellurideLauncherFile();
        if (launcherBinary == null) {
            fail("[TellurideTests][testGettingLauncherFile] no launcher found");
        }
        if (!launcherBinary.exists()) {
            LOG.warn("[TellurideTests][testGettingLauncherFile] aborting this test (" + launcherBinary.getAbsolutePath() + "), launcher file does not exist");
            return;
        }
        if (!launcherBinary.canExecute()) {
            fail("[TellurideTests][testGettingLauncherFile] launcher is not executable (" + launcherBinary.getAbsolutePath() + ")");
        }
    }
}