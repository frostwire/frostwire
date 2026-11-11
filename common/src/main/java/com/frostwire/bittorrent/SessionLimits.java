/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

/**
 * The libtorrent session's connection limits, transfer rate limits and DHT state, as the Options
 * window shows them. Reading each of these from the session is a synchronous call into libtorrent's
 * session thread, which can take seconds while that thread is busy, so the UI reads this copy
 * instead (frostwire/frostwire#1529).
 */
public record SessionLimits(
    int maxConnections,
    int maxPeers,
    int maxActiveDownloads,
    int maxActiveSeeds,
    int downloadRateLimit,
    int uploadRateLimit,
    boolean dhtRunning) {

  public SessionLimits withMaxConnections(int value) {
    return new SessionLimits(
        value,
        maxPeers,
        maxActiveDownloads,
        maxActiveSeeds,
        downloadRateLimit,
        uploadRateLimit,
        dhtRunning);
  }

  public SessionLimits withMaxPeers(int value) {
    return new SessionLimits(
        maxConnections,
        value,
        maxActiveDownloads,
        maxActiveSeeds,
        downloadRateLimit,
        uploadRateLimit,
        dhtRunning);
  }

  public SessionLimits withMaxActiveDownloads(int value) {
    return new SessionLimits(
        maxConnections,
        maxPeers,
        value,
        maxActiveSeeds,
        downloadRateLimit,
        uploadRateLimit,
        dhtRunning);
  }

  public SessionLimits withMaxActiveSeeds(int value) {
    return new SessionLimits(
        maxConnections,
        maxPeers,
        maxActiveDownloads,
        value,
        downloadRateLimit,
        uploadRateLimit,
        dhtRunning);
  }

  public SessionLimits withDownloadRateLimit(int value) {
    return new SessionLimits(
        maxConnections,
        maxPeers,
        maxActiveDownloads,
        maxActiveSeeds,
        value,
        uploadRateLimit,
        dhtRunning);
  }

  public SessionLimits withUploadRateLimit(int value) {
    return new SessionLimits(
        maxConnections,
        maxPeers,
        maxActiveDownloads,
        maxActiveSeeds,
        downloadRateLimit,
        value,
        dhtRunning);
  }

  public SessionLimits withDhtRunning(boolean value) {
    return new SessionLimits(
        maxConnections,
        maxPeers,
        maxActiveDownloads,
        maxActiveSeeds,
        downloadRateLimit,
        uploadRateLimit,
        value);
  }
}
