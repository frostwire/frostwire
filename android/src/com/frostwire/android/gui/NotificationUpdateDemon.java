package com.frostwire.android.gui;

import android.content.Context;

import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.logging.Logger;

import java.lang.ref.WeakReference;

public class NotificationUpdateDemon implements TimerObserver {
    private static final Logger LOG = Logger.getLogger(NotificationUpdateDemon.class);
    private static final int FROSTWIRE_STATUS_NOTIFICATION_UPDATE_INTERVAL_IN_SECS = 5;

    private Context parentContext;
    private TimerSubscription subscription;

    public NotificationUpdateDemon(Context parentContext) {
        this.parentContext = parentContext;
    }

    public void start(){
        if(subscription!=null){
            subscription.unsubscribe();
        }
        subscription = TimerService.subscribe(this,FROSTWIRE_STATUS_NOTIFICATION_UPDATE_INTERVAL_IN_SECS);
    }

    public void stop(){
        subscription.unsubscribe();
    }

    private void updatePermanentStatusNotification() {
        //  format strings
        String sDown = UIUtils.rate2speed(TransferManager.instance().getDownloadsBandwidth() / 1024);
        String sUp = UIUtils.rate2speed(TransferManager.instance().getUploadsBandwidth() / 1024);

        // number of uploads (seeding) and downloads
        int downloads = TransferManager.instance().getActiveDownloads();
        int uploads = TransferManager.instance().getActiveUploads();

        EngineService.updatePermanentStatusNotification(
                    new WeakReference<>(parentContext),
                    downloads,
                    sDown,
                    uploads,
                    sUp);
    }

    @Override
    public void onTime() {
        updatePermanentStatusNotification();
    }
}
