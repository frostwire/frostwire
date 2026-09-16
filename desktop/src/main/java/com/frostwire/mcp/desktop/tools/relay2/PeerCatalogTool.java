/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.CatalogBrowser;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.Collections;
import java.util.List;

/**
 * MCP tool that fetches a remote peer's shared-torrent catalog over IceBridge.
 *
 * <p>Delegates to {@link CatalogBrowser}, which sends a signed catalog-browse request over the
 * relay transport and verifies the peer-signed manifest that comes back. Peers only answer when
 * they have opted in to {@code PUBLIC_CATALOG}; otherwise the catalog is returned empty. No peer
 * IPs are ever included in the response.
 */
public final class PeerCatalogTool implements MCPTool {

  private static final Logger LOG = Logger.getLogger(PeerCatalogTool.class);

  private static final int DEFAULT_TIMEOUT_MS = 5000;
  private static final int MIN_TIMEOUT_MS = 100;
  private static final int MAX_TIMEOUT_MS = 30000;

  @Override
  public String name() {
    return "frostwire_peer_catalog";
  }

  @Override
  public String description() {
    return "Fetch a peer's shared-torrent catalog over IceBridge by its ed25519 pubkey. Peers only answer if they have opted in to PUBLIC_CATALOG; otherwise the catalog is empty. Returns no IP addresses.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject peerProp = new JsonObject();
    peerProp.addProperty("type", "string");
    peerProp.addProperty(
        "description", "Peer ed25519 pubkey as 64-character hex or base64url (32 raw bytes)");
    props.add("peer", peerProp);

    JsonObject timeoutProp = new JsonObject();
    timeoutProp.addProperty("type", "integer");
    timeoutProp.addProperty(
        "description", "Fetch timeout in milliseconds (default 5000, clamp 100..30000)");
    props.add("timeout_ms", timeoutProp);

    schema.add("properties", props);

    JsonArray required = new JsonArray();
    required.add("peer");
    schema.add("required", required);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      String rawPeer = RelayToolSupport.stringArg(arguments, "peer");
      byte[] pubBytes;
      try {
        pubBytes = RelayToolSupport.parsePub(rawPeer);
      } catch (IllegalArgumentException e) {
        out.addProperty("error", e.getMessage());
        return out;
      }
      String peer = Hex.encode(pubBytes);

      DistributedSearchTransport transport = SearchEngine.getDistributedSearchTransport();
      IdentityKeys identity = SearchEngine.getDistributedIdentity();
      if (transport == null || identity == null) {
        out.addProperty("error", "distributed relay not wired");
        return out;
      }

      int timeoutMs =
          RelayToolSupport.clampInt(
              arguments, "timeout_ms", DEFAULT_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);

      List<RemoteIndexFetcher.RemoteTorrentEntry> entries;
      try {
        entries = new CatalogBrowser(identity, transport).fetchCatalog(pubBytes, timeoutMs);
      } catch (Throwable t) {
        LOG.debug("peer catalog fetch failed for " + peer, t);
        entries = null;
      }
      if (entries == null) {
        entries = Collections.emptyList();
      }

      JsonArray catalog = new JsonArray();
      for (RemoteIndexFetcher.RemoteTorrentEntry entry : entries) {
        JsonObject row = new JsonObject();
        row.addProperty("ih", entry.infoHashHex());
        row.addProperty("name", entry.name());
        row.addProperty("size_bytes", entry.sizeBytes());
        row.addProperty("files", entry.fileCount());
        catalog.add(row);
      }

      out.addProperty("count", catalog.size());
      out.addProperty("peer", peer);
      out.add("catalog", catalog);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
