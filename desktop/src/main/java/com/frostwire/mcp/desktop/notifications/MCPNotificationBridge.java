package com.frostwire.mcp.desktop.notifications;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.adapters.SearchAdapter;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.frostwire.transfers.TransferState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.VPNs;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.frostwire.util.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MCPNotificationBridge implements BTEngineListener, SearchListener {

    private static final Logger LOG = Logger.getLogger(MCPNotificationBridge.class);

    private static final MCPNotificationBridge INSTANCE = new MCPNotificationBridge();

    private static final long PROGRESS_INTERVAL_MS = 1000;
    private static final long VPN_POLL_INTERVAL_MS = 20000;

    private volatile MCPServer server;
    private BTEngineListener delegateBTEngineListener;
    private SearchListener delegateSearchListener;
    private final Map<String, TransferState> lastStates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastProgressTime = new ConcurrentHashMap<>();
    private volatile boolean registered;
    private final ScheduledExecutorService vpnScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MCP-VPN-Monitor");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> vpnPollTask;
    private volatile boolean lastKnownVpnStatus;

    private MCPNotificationBridge() {
    }

    public static MCPNotificationBridge instance() {
        return INSTANCE;
    }

    public void setServer(MCPServer server) {
        this.server = server;
        if (server != null && !registered) {
            registerListeners();
        } else if (server == null && registered) {
            unregisterListeners();
        }
    }

    private void registerListeners() {
        try {
            BTEngine engine = BTEngine.getInstance();
            delegateBTEngineListener = engine.getListener();
            engine.setListener(this);

            SearchManager searchManager = SearchManager.getInstance();
            delegateSearchListener = searchManager.getListener();
            searchManager.setListener(this);

            lastKnownVpnStatus = VPNs.isVPNActive();
            vpnPollTask = vpnScheduler.scheduleAtFixedRate(this::pollVpnStatus,
                    VPN_POLL_INTERVAL_MS, VPN_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

            registered = true;
        } catch (Throwable e) {
            LOG.error("Failed to register MCP notification bridge listeners: " + e.getMessage(), e);
        }
    }

    private void unregisterListeners() {
        try {
            if (vpnPollTask != null) {
                vpnPollTask.cancel(false);
                vpnPollTask = null;
            }

            BTEngine engine = BTEngine.getInstance();
            if (delegateBTEngineListener != null) {
                engine.setListener(delegateBTEngineListener);
            }

            SearchManager searchManager = SearchManager.getInstance();
            if (delegateSearchListener != null) {
                searchManager.setListener(delegateSearchListener);
            }

            registered = false;
        } catch (Throwable e) {
            LOG.error("Failed to unregister MCP notification bridge listeners: " + e.getMessage(), e);
        }
    }

    private void pollVpnStatus() {
        try {
            boolean current = VPNs.isVPNActive();
            if (current != lastKnownVpnStatus) {
                lastKnownVpnStatus = current;
                VPNStatusNotification notif = new VPNStatusNotification(
                        current,
                        ConnectionSettings.VPN_DROP_PROTECTION.getValue());
                sendNotification(notif.method(), notif.payload());
            }
        } catch (Throwable e) {
            LOG.warn("VPN poll error: " + e.getMessage());
        }
    }

    private void sendNotification(String method, JsonObject params) {
        MCPServer s = server;
        if (s != null) {
            try {
                s.sendNotification(method, params);
            } catch (Throwable e) {
                LOG.warn("Failed to send MCP notification: " + e.getMessage());
            }
        }
    }

    @Override
    public void started(BTEngine engine) {
        if (delegateBTEngineListener != null) {
            delegateBTEngineListener.started(engine);
        }
    }

    @Override
    public void stopped(BTEngine engine) {
        lastStates.clear();
        lastProgressTime.clear();
        if (delegateBTEngineListener != null) {
            delegateBTEngineListener.stopped(engine);
        }
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
        if (dl != null && dl.getInfoHash() != null) {
            lastStates.put(dl.getInfoHash(), dl.getState());
            TransferStateNotification notif = new TransferStateNotification(
                    dl.getInfoHash(), "UNKNOWN", dl.getState().name(), dl.getProgress());
            sendNotification(notif.method(), notif.payload());
        }
        if (delegateBTEngineListener != null) {
            delegateBTEngineListener.downloadAdded(engine, dl);
        }
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
        if (dl != null && dl.getInfoHash() != null) {
            TransferState newState = dl.getState();
            TransferState oldState = lastStates.get(dl.getInfoHash());

            if (oldState != newState) {
                JsonObject params = new JsonObject();
                params.addProperty("downloadId", dl.getInfoHash());
                params.addProperty("oldState", oldState != null ? oldState.name() : "UNKNOWN");
                params.addProperty("newState", newState.name());
                params.addProperty("progress", dl.getProgress());
                sendNotification("notifications/transfer_state_changed", params);
                lastStates.put(dl.getInfoHash(), newState);
            }

            if (dl.isDownloading()) {
                long now = System.currentTimeMillis();
                Long lastTime = lastProgressTime.get(dl.getInfoHash());
                if (lastTime == null || (now - lastTime) > PROGRESS_INTERVAL_MS) {
                    TransferProgressNotification notif = new TransferProgressNotification(
                            dl.getInfoHash(),
                            dl.getProgress(),
                            dl.getDownloadSpeed(),
                            dl.getUploadSpeed(),
                            dl.getETA(),
                            dl.getBytesReceived(),
                            dl.getBytesSent());
                    sendNotification(notif.method(), notif.payload());
                    lastProgressTime.put(dl.getInfoHash(), now);
                }
            }
        }
        if (delegateBTEngineListener != null) {
            delegateBTEngineListener.downloadUpdate(engine, dl);
        }
    }

    @Override
    public void onResults(long token, List<? extends SearchResult> results) {
        if (server != null && !results.isEmpty()) {
            JsonArray preview = new JsonArray();
            int limit = Math.min(results.size(), 5);
            for (int i = 0; i < limit; i++) {
                preview.add(SearchAdapter.toMCPJson(results.get(i)));
            }
            SearchResultNotification notif = new SearchResultNotification(
                    String.valueOf(token), results.size(), preview);
            sendNotification(notif.method(), notif.payload());
        }
        if (delegateSearchListener != null) {
            delegateSearchListener.onResults(token, results);
        }
    }

    @Override
    public void onStopped(long token) {
        if (server != null) {
            SearchCompletedNotification notif = new SearchCompletedNotification(String.valueOf(token));
            sendNotification(notif.method(), notif.payload());
        }
        if (delegateSearchListener != null) {
            delegateSearchListener.onStopped(token);
        }
    }

    @Override
    public void onError(long token, SearchError error) {
        if (delegateSearchListener != null) {
            delegateSearchListener.onError(token, error);
        }
    }
}
