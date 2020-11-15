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

import com.frostwire.search.telluride.TellurideLauncher;
import com.frostwire.search.telluride.TellurideListener;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.fail;

public class TellurideTests {
    static String executableSuffix = ".exe";

    static {
        if (OSUtils.isAnyMac()) {
            executableSuffix = "_macos";
        } else if (OSUtils.isLinux()) {
            executableSuffix = "_linux";
        }

        File data = new File("/Users/gubatron/FrostWire/Torrent Data", "Video_by_gubatron-CDC5ludJazw.mp4");
        if (data.exists()) {
            data.delete();
        }
    }

    static boolean progressWasReported = false;

    @Test
    public void testConnectionError() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> failedTests = new ArrayList<>();
        TellurideListener tellurideListener = new TellurideListener() {
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
                System.out.println("[TellurideTests][testConnectionError] onProgress(completionPercentage=" +
                        completionPercentage + ", fileSize=" +
                        fileSize + ", fileSizeUnits=" +
                        fileSizeUnits + ", downloadSpeed=" +
                        downloadSpeed + ", downloadSpeedUnits=" +
                        downloadSpeedUnits + ", ETA=" + ETA + ")");
                progressWasReported = true;
            }

            @Override
            public void onError(String errorMessage) {
                System.out.println("[TellurideTests][testConnectionError] (expected) onError(errorMessage=" + errorMessage + ")");
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
                System.out.println("[TellurideTests][testConnectionError] onDestination(outputFilename=" + outputFilename + ")");
            }

            @Override
            public boolean aborted() {
                return false;
            }

            @Override
            public void onMeta(String json) {
                System.out.println("[TellurideTests][testConnectionError] GOT JSON!");
                System.out.println(json);
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        TellurideLauncher.launch(new File("/Users/gubatron/workspace.frostwire/frostwire/telluride/telluride" + executableSuffix),
                "https://www.youtube.com/watch?v=fail",
                new File("/Users/gubatron/FrostWire/Torrent Data"),
                false,
                false,
                true,
                tellurideListener);

        System.out.println("[TellurideTests][testConnectionError] waiting...");
        latch.await();
        if (failedTests.size() > 0) {
            fail("[TellurideTests][testConnectionError] isn't failing as expected ;)");
        }
        System.out.println("[TellurideTests][testConnectionError] finished.");
    }

    @Test
    public void testDownload() throws InterruptedException {
        progressWasReported = false;
        CountDownLatch latch = new CountDownLatch(1);
        List<String> failedTests = new ArrayList<>();
        TellurideListener tellurideListener = new TellurideListener() {
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
                System.out.println("[TellurideTests][testDownload] onProgress(completionPercentage=" +
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
                if (!progressWasReported) {
                    failedTests.add("[TellurideTests][testDownload] Failed, did not receive onProgress call. onFinished(exitCode=" + exitCode + ")");
                }
                latch.countDown();
            }

            @Override
            public void onDestination(String outputFilename) {
                System.out.println("[TellurideTests][testDownload] onDestination(outputFilename=" + outputFilename + ")");
            }

            @Override
            public boolean aborted() {
                return false;
            }

            @Override
            public void onMeta(String json) {
                System.out.println("[TellurideTests][testDownload] GOT JSON!");
                System.out.println(json);
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        TellurideLauncher.launch(new File("/Users/gubatron/workspace.frostwire/frostwire/telluride/telluride" + executableSuffix),
                "https://www.instagram.com/tv/CDC5ludJazw/", // Backing up an Instagram Live video: Bouldering Session at Movement Baker by Gubatron
                new File("/Users/gubatron/FrostWire/Torrent Data"),
                false,
                false,
                true,
                tellurideListener);

        System.out.println("[TellurideTests][testDownload] waiting...");
        latch.await();
        if (failedTests.size() > 0) {
            fail(failedTests.get(0));
        }
        System.out.println("[TellurideTests][testDownload] finished.");
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
                true,
                tellurideListener);

        System.out.println("[TellurideTests][testMetaOnly] waiting...");
        latch.await();
        if (failedTests.size() > 0) {
            fail(failedTests.get(0));
        }
        System.out.println("[TellurideTests][testMetaOnly] finished.");
    }

    @Test
    public void testGettingLauncherFile() {
        File launcherBinary = FrostWireUtils.getTellurideLauncherFile();
        if (launcherBinary == null) {
            fail("[TellurideTests][testGettingLauncherFile] no launcher found");
            return;
        }
        if (!launcherBinary.canExecute()) {
            fail("[TellurideTests][testGettingLauncherFile] launcher is not executable (" + launcherBinary.getAbsolutePath() + ")");
            return;
        }
    }
}