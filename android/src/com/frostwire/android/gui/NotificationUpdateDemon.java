package com.frostwire.android.gui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.logging.Logger;

public class NotificationUpdateDemon implements TimerObserver{

    private static final Logger LOG = Logger.getLogger(NotificationUpdateDemon.class);
    private static final int FROSTWIRE_STATUS_NOTIFICATION_UPDATE_INTERVAL_IN_SECS = 5;

    private Context mParentContext;
    private TimerSubscription mTimerSubscription;

    public NotificationUpdateDemon(Context parentContext) {
        LOG.debug("Creating permanent notification demon");
        mParentContext = parentContext;
    }

    public void start() {
        LOG.debug("Starting permanent notification demon");
        if (mTimerSubscription != null) {
            LOG.debug("Stopping before (re)starting permanent notification demon");
            mTimerSubscription.unsubscribe();
        }
        mTimerSubscription = TimerService.subscribe(this, FROSTWIRE_STATUS_NOTIFICATION_UPDATE_INTERVAL_IN_SECS);
    }

    public void stop() {
        LOG.debug("Stopping permanent notification demon");
        mTimerSubscription.unsubscribe();
    }

    private void updatePermanentStatusNotification() {
        //  format strings
        String sDown = UIUtils.rate2speed(TransferManager.instance().getDownloadsBandwidth() / 1024);
        String sUp = UIUtils.rate2speed(TransferManager.instance().getUploadsBandwidth() / 1024);

        // number of uploads (seeding) and downloads
        int downloads = TransferManager.instance().getActiveDownloads();
        int uploads = TransferManager.instance().getActiveUploads();

        RemoteViews remoteViews = new RemoteViews(mParentContext.getPackageName(),
                R.layout.view_permanent_status_notification);

        PendingIntent showFrostWireIntent = createShowFrostwireIntent();
//      PendingIntent showVPNIntent = createShowVPNIntent();
        PendingIntent shutdownIntent = createShutdownIntent();


        // VPN status
//        remoteViews.setImageViewResource(R.id.view_permanent_status_vpn_icon, isVPNactive ?
//                R.drawable.notification_shutdown : R.drawable.notification_shutdown);
//        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_vpn_icon, showVPNIntent);


        // Click on shutdown image button.
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_shutdown, shutdownIntent);

        // Click on title takes to transfers.
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_text_title, showFrostWireIntent);

        // Transfers status.
        remoteViews.setTextViewText(R.id.view_permanent_status_text_downloads, downloads + " @ " + sDown);
        remoteViews.setTextViewText(R.id.view_permanent_status_text_uploads, uploads + " @ " + sUp);

        Notification notification = new NotificationCompat.Builder(mParentContext).
                setSmallIcon(R.drawable.frostwire_notification_flat).
                setContentIntent(showFrostWireIntent).
                setContent(remoteViews).
                build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        final NotificationManager notificationManager = (NotificationManager) mParentContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(EngineService.FROSTWIRE_STATUS_NOTIFICATION, notification);
        }
    }

    private PendingIntent createShowFrostwireIntent(){
        return PendingIntent.getActivity(mParentContext,
                0,
                new Intent(mParentContext,
                        MainActivity.class).
                        setAction(Constants.ACTION_SHOW_TRANSFERS).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                0);
    }

    private PendingIntent createShutdownIntent(){
        return PendingIntent.getActivity(mParentContext,
                1,
                new Intent(mParentContext,
                        MainActivity.class).
                        setAction(Constants.ACTION_REQUEST_SHUTDOWN).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                0);
    }

//    private PendingIntent createShowVPNIntent(){
//        return PendingIntent.getActivity(mParentContext,
//                1,
//                new Intent(mParentContext,
//                        VPNStatusDetailActivity.class).
//                        setAction(isVPNactive ?
//                                Constants.ACTION_SHOW_VPN_STATUS_PROTECTED :
//                                Constants.ACTION_SHOW_VPN_STATUS_UNPROTECTED).
//                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
//                0);
//    }

    @Override
    public void onTime() {
        updatePermanentStatusNotification();
    }

    @Override
    public boolean canBeSkipped() {
        return !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION);
    }
}
