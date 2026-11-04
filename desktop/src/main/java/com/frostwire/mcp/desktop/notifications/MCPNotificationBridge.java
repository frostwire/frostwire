/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.notifications;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.adapters.SearchAdapter;
import com.frostwire.mcp.desktop.state.SearchSessionManager;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.BTEngineListenerChain;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.VPNs;
import com.limegroup.gnutella.settings.ConnectionSettings;
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
  private SearchListener delegateSearchListener;
  private final Map<String, TransferState> lastStates = new ConcurrentHashMap<>();
  private final Map<String, Long> lastProgressTime = new ConcurrentHashMap<>();
  private volatile boolean registered;
  private final ScheduledExecutorService vpnScheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "MCP-VPN-Monitor");
            t.setDaemon(true);
            return t;
          });
  private ScheduledFuture<?> vpnPollTask;
  private volatile boolean lastKnownVpnStatus;

  private MCPNotificationBridge() {}

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
      // Joins the engine's listeners instead of standing in front of them: listeners added later
      // (the download manager's, at startup) chain this bridge, so wrapping them again on a restart
      // would make it call itself.
      BTEngineListenerChain.install(BTEngine.getInstance(), this);

      // The SearchManager has a single listener; never take this bridge for the one it wraps.
      SearchManager searchManager = SearchManager.getInstance();
      SearchListener searchListener = searchManager.getListener();
      delegateSearchListener = searchListener != this ? searchListener : null;
      searchManager.setListener(this);

      lastKnownVpnStatus = VPNs.isVPNActive();
      vpnPollTask =
          vpnScheduler.scheduleAtFixedRate(
              this::pollVpnStatus,
              VPN_POLL_INTERVAL_MS,
              VPN_POLL_INTERVAL_MS,
              TimeUnit.MILLISECONDS);

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

      BTEngineListenerChain.remove(BTEngine.getInstance(), this);

      // Put back what was there, even none, unless someone has replaced the bridge since.
      SearchManager searchManager = SearchManager.getInstance();
      if (searchManager.getListener() == this) {
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
        VPNStatusNotification notif =
            new VPNStatusNotification(current, ConnectionSettings.VPN_DROP_PROTECTION.getValue());
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
  public void started(BTEngine engine) {}

  @Override
  public void stopped(BTEngine engine) {
    lastStates.clear();
    lastProgressTime.clear();
  }

  @Override
  public void downloadAdded(BTEngine engine, BTDownload dl) {
    if (dl != null && dl.getInfoHash() != null) {
      lastStates.put(dl.getInfoHash(), dl.getState());
      TransferStateNotification notif =
          new TransferStateNotification(
              dl.getInfoHash(), "UNKNOWN", dl.getState().name(), dl.getProgress());
      sendNotification(notif.method(), notif.payload());
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
          TransferProgressNotification notif =
              new TransferProgressNotification(
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
  }

  @Override
  public void onResults(long token, List<? extends SearchResult> results) {
    // The SearchManager has one listener, this bridge: MCP searches get their results through it.
    SearchSessionManager.instance().onResults(token, results);
    if (server != null && !results.isEmpty()) {
      JsonArray preview = new JsonArray();
      int limit = Math.min(results.size(), 5);
      for (int i = 0; i < limit; i++) {
        preview.add(SearchAdapter.toMCPJson(results.get(i)));
      }
      SearchResultNotification notif =
          new SearchResultNotification(String.valueOf(token), results.size(), preview);
      sendNotification(notif.method(), notif.payload());
    }
    if (delegateSearchListener != null) {
      delegateSearchListener.onResults(token, results);
    }
  }

  @Override
  public void onStopped(long token) {
    SearchSessionManager.instance().onStopped(token);
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
    SearchSessionManager.instance().onError(token, error);
    if (delegateSearchListener != null) {
      delegateSearchListener.onError(token, error);
    }
  }
}
