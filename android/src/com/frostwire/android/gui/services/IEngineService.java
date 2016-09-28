/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.services;

import android.app.Application;
import com.frostwire.android.core.player.CoreMediaPlayer;

import java.io.File;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 *
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

    ExecutorService getThreadPool();

    void notifyDownloadFinished(String displayName, File file);
    
    Application getApplication();

    void shutdown();
}
