/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.util.Logger;

public class TorrentEngineWorker extends Worker {
    private static final Logger LOG = Logger.getLogger(TorrentEngineWorker.class);

    public TorrentEngineWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            LOG.info("TorrentEngineWorker:doWork(): Starting Torrent Engine...");
            BTEngine.getInstance().start();
            return Result.success();
        } catch (Exception e) {
            LOG.error("TorrentEngineWorker:doWork(): Error starting Torrent Engine", e);
            return Result.failure();
        }
    }
}
