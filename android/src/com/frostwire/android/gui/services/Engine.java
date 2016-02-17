/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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
import android.content.*;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.services.EngineService.EngineServiceBinder;
import com.frostwire.android.util.BloomFilter;
import com.frostwire.logging.Logger;

import java.io.*;
import java.util.BitSet;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Engine implements IEngineService {
    private static final Logger LOG = Logger.getLogger(Engine.class);
    private EngineService service;
    private ServiceConnection connection;
    private EngineBroadcastReceiver receiver;
    private BloomFilter<String> notifiedDownloads;
    private final File notifiedDat;

    private static Engine instance;

    public synchronized static void create(Application context) {
        if (instance != null) {
            return;
        }
        instance = new Engine(context);
    }

    public static Engine instance() {
        if (instance == null) {
            throw new RuntimeException("Engine not created");
        }
        return instance;
    }

    private Engine(Application context) {
        notifiedDat = new File(context.getExternalFilesDir(null), "notified.dat");
        loadNotifiedDownloads();
        startEngineService(context);
    }

    /**
     * loads a dictionary of infohashes that have been already
     * notified from notified.dat. This binary file packs together
     * infohashes 20 bytes at the time.
     * <p/>
     * this method goes through the file, 20 bytes at the time and populates
     * a HashMap we can use to query wether or not we should notify the user
     * in constant time.
     * <p/>
     * When we have a new infohash, the file conveniently grows by appending the
     * new 20 bytes of the new hash.
     */
    private void loadNotifiedDownloads() {
        notifiedDownloads = new BloomFilter<String>(Constants.NOTIFIED_BLOOM_FILTER_BITSET_SIZE,
                Constants.NOTIFIED_BLOOM_FILTER_EXPECTED_ELEMENTS);

        if (!notifiedDat.exists()) {
            try {
                notifiedDat.createNewFile();
            } catch (Throwable e) {
                e.printStackTrace();
                LOG.error("Could not create notified.dat", e);
            }
        } else if (notifiedDat.length() > 0) {
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(notifiedDat));
                int numberOfElements = ois.readInt();
                BitSet bs = (BitSet) ois.readObject();
                ois.close();
                notifiedDownloads = new BloomFilter<String>(
                        Constants.NOTIFIED_BLOOM_FILTER_BITSET_SIZE,
                        Constants.NOTIFIED_BLOOM_FILTER_EXPECTED_ELEMENTS,
                        numberOfElements,
                        bs);
                LOG.info("Loaded bloom filter from notified.dat sucessfully (" + numberOfElements + " elements)");
            } catch (Throwable e) {
                LOG.error("Error reading notified.dat", e);

                // reset the file in case we changed the format or something was borked.
                // worst case we'll have a new bloom filter.
                notifiedDat.delete();
                try {
                    notifiedDat.createNewFile();
                    LOG.info("Created new notified.dat file.");
                } catch (IOException e1) {
                    LOG.error(e1.getMessage(), e1);
                }
            }
        }
    }

    @Override
    public CoreMediaPlayer getMediaPlayer() {
        return service != null ? service.getMediaPlayer() : null;
    }

    public byte getState() {
        return service != null ? service.getState() : IEngineService.STATE_INVALID;
    }

    public boolean isStarted() {
        return service != null ? service.isStarted() : false;
    }

    public boolean isStarting() {
        return service != null ? service.isStarting() : false;
    }

    public boolean isStopped() {
        return service != null ? service.isStopped() : false;
    }

    public boolean isStopping() {
        return service != null ? service.isStopping() : false;
    }

    public boolean isDisconnected() {
        return service != null ? service.isDisconnected() : false;
    }

    public void startServices() {
        if (service != null) {
            service.startServices();
        }
    }

    public void stopServices(boolean disconnected) {
        if (service != null) {
            service.stopServices(disconnected);
        }
    }

    public ExecutorService getThreadPool() {
        return EngineService.threadPool;
    }

    public BloomFilter<String> getNotifiedDownloadsBloomFilter() {
        return notifiedDownloads;
    }

    public void notifyDownloadFinished(String displayName, File file, String optionalInfoHash) {
        if (service != null) {
            if (optionalInfoHash != null && !optionalInfoHash.equals("0000000000000000000000000000000000000000")) {
                if (!updateNotifiedTorrentDownloads(optionalInfoHash)) {
                    // did not update, we already knew about it. skip notification.
                    return;
                }
            }
            service.notifyDownloadFinished(displayName, file);
        }
    }

    private boolean updateNotifiedTorrentDownloads(String optionalInfoHash) {
        boolean result = false;
        optionalInfoHash = optionalInfoHash.toLowerCase().trim();
        if (notifiedDownloads.contains(optionalInfoHash)) {
            LOG.info("Skipping notification on " + optionalInfoHash);
        } else {
            result = addNewNotifiedInfoHash(optionalInfoHash);
        }
        return result;
    }

    private boolean addNewNotifiedInfoHash(String infoHash) {
        boolean result = false;
        if (notifiedDownloads != null && infoHash != null && infoHash.length() == 40) {
            infoHash = infoHash.toLowerCase().trim();
            try {
                // Another partial download might have just finished writing
                // this info hash while I was waiting for the file lock.
                if (!notifiedDownloads.contains(infoHash)) {
                    notifiedDownloads.add(infoHash);
                    synchronized (notifiedDat) {
                        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(notifiedDat));
                        oos.writeInt(notifiedDownloads.count());
                        oos.writeObject(notifiedDownloads.getBitSet());
                        oos.flush();
                        oos.close();
                    }
                    result = true;
                }
            } catch (Throwable e) {
                LOG.error("Could not update infohash on notified.dat", e);
                result = false;
            }
        }
        return result;
    }

    public void notifyDownloadFinished(String displayName, File file) {
        notifyDownloadFinished(displayName, file, null);
    }

    @Override
    public void shutdown() {
        if (service != null) {
            if (connection != null) {
                try {
                    getApplication().unbindService(connection);
                } catch (IllegalArgumentException e) {
                }
            }

            if (receiver != null) {
                try {
                    getApplication().unregisterReceiver(receiver);
                } catch (IllegalArgumentException e) {
                }
            }

            service.shutdown();
        }
    }

    /**
     * @param context This must be the application context, otherwise there will be a leak.
     */
    private void startEngineService(final Context context) {
        Intent i = new Intent();
        i.setClass(context, EngineService.class);
        context.startService(i);
        context.bindService(i, connection = new ServiceConnection() {
            public void onServiceDisconnected(ComponentName name) {
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                // avoids: java.lang.ClassCastException: android.os.BinderProxy cannot be cast to com.frostwire.android.gui.services.EngineService$EngineServiceBinder
                if (service instanceof EngineServiceBinder) {
                    Engine.this.service = ((EngineServiceBinder) service).getService();
                    registerStatusReceiver(context);
                }
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void registerStatusReceiver(Context context) {
        receiver = new EngineBroadcastReceiver();

        IntentFilter wifiFilter = new IntentFilter();

        wifiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);

        IntentFilter fileFilter = new IntentFilter();

        fileFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        fileFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
        fileFilter.addAction(Intent.ACTION_MEDIA_CHECKING);
        fileFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        fileFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        fileFilter.addAction(Intent.ACTION_MEDIA_NOFS);
        fileFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        fileFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        fileFilter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        fileFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        fileFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        fileFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        fileFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        fileFilter.addAction(Intent.ACTION_UMS_CONNECTED);
        fileFilter.addAction(Intent.ACTION_UMS_DISCONNECTED);
        fileFilter.addDataScheme("file");

        IntentFilter connectivityFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        IntentFilter audioFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        IntentFilter telephonyFilter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        context.registerReceiver(receiver, wifiFilter);
        context.registerReceiver(receiver, fileFilter);
        context.registerReceiver(receiver, connectivityFilter);
        context.registerReceiver(receiver, audioFilter);
        context.registerReceiver(receiver, telephonyFilter);
    }

    @Override
    public Application getApplication() {
        Application r = null;
        if (service != null) {
            r = service.getApplication();
        }
        return r;
    }
}
