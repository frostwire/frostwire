/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.services;

import android.app.Application;

import com.frostwire.android.core.player.CoreMediaPlayer;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public interface IEngineService {

    byte STATE_INVALID = -1;
    byte STATE_STARTED = 10;
    byte STATE_STARTING = 11;
    byte STATE_STOPPED = 12;
    byte STATE_STOPPING = 13;
    byte STATE_DISCONNECTED = 14;

    CoreMediaPlayer getMediaPlayer();

    byte getState();

    boolean isStarted();

    boolean isStarting();

    boolean isStopped();

    boolean isStopping();

    boolean isDisconnected();

    void startServices();

    void stopServices(boolean disconnected);

    /**
     * @param displayName the display name to show in the notification
     * @param file        the file to open
     * @param infoHash    the optional info hash if available
     */
    void notifyDownloadFinished(String displayName, File file, String infoHash);

    Application getApplication();

    void shutdown();
}
