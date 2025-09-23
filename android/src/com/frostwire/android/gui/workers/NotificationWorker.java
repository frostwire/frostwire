/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.workers;

import android.app.NotificationManager;
import android.content.Context;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NotificationUpdateDaemon;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;

public class NotificationWorker extends Worker {
    private static final Logger LOG = Logger.getLogger(NotificationWorker.class);

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            LOG.info("NotificationWorker:doWork() - Updating notification directly");
            Context context = getApplicationContext();
            
            // Instead of creating a new NotificationUpdateDaemon (which would schedule more work),
            // just update the notification directly using the same logic
            TransferManager transferManager = TransferManager.instance();
            
            if (transferManager == null) {
                LOG.warn("TransferManager instance is null. Skipping notification update.");
                return Result.success();
            }

            int downloads = transferManager.getActiveDownloads();
            int uploads = transferManager.getActiveUploads();

            if (downloads == 0 && uploads == 0) {
                LOG.info("No active transfers. Clearing notification.");
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.cancel(Constants.NOTIFICATION_FROSTWIRE_STATUS);
                }
                return Result.success();
            }

            // Create a temporary daemon just to get the notification object, don't start it
            NotificationUpdateDaemon tempDaemon = new NotificationUpdateDaemon(context);
            
            // Update notification directly
            String sDown = UIUtils.rate2speed((double) transferManager.getDownloadsBandwidth() / 1024);
            String sUp = UIUtils.rate2speed(transferManager.getUploadsBandwidth() / 1024);

            RemoteViews notificationViews = tempDaemon.getNotificationViews();
            if (notificationViews != null) {
                notificationViews.setTextViewText(R.id.view_permanent_status_text_downloads, downloads + " @ " + sDown);
                notificationViews.setTextViewText(R.id.view_permanent_status_text_uploads, uploads + " @ " + sUp);
            }

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(Constants.NOTIFICATION_FROSTWIRE_STATUS, tempDaemon.getNotificationObject());
            }
            
            return Result.success();
        } catch (Exception e) {
            LOG.error("NotificationWorker:doWork() failed", e);
            return Result.failure();
        }
    }
}
