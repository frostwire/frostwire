/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes the karma chain's newest entries as BEP 46 mutable DHT items signed with the node's
 * Ed25519 key, in the paged form {@link KarmaChainPages} describes: the head under {@link
 * KarmaConstants#BEP46_SALT_KARMA}, older pages under {@code frostwire-karma-v1/<n>}.
 *
 * <p>Every non-empty chain is published, a lone epoch commitment included, so the items are put
 * again on every call and don't expire from the DHT while the chain has no new endorsement.
 *
 * <p>Thread-safe: stateless aside from the in-memory chain it reads. The chain is itself
 * synchronized, so this publisher can be called from any thread.
 */
public final class KarmaChainPublisher {

  private static final Logger LOG = Logger.getLogger(KarmaChainPublisher.class);

  /** BEP 44 mutable item value size limit (conservative), per page. */
  static final int MAX_MANIFEST_BYTES = KarmaChainPages.MAX_PAGE_BYTES;

  static final int MANIFEST_VERSION = KarmaChainPages.VERSION;

  private final KarmaChainWriter writer;
  private final IdentityKeys identity;

  public KarmaChainPublisher(KarmaChainWriter writer, IdentityKeys identity) {
    if (writer == null) {
      throw new IllegalArgumentException("writer is null");
    }
    if (identity == null) {
      throw new IllegalArgumentException("identity is null");
    }
    this.writer = writer;
    this.identity = identity;
  }

  /**
   * Builds and publishes the chain's pages. Returns the number of entries published over all pages,
   * or 0 if nothing was published (empty chain or DHT not available).
   */
  public int publishIfNeeded(SessionManager session) {
    if (session == null) {
      return 0;
    }
    try {
      List<KarmaChainEntry> chain = writer.chain().entries();
      List<Entry> pages = buildPages(chain);
      if (pages.isEmpty()) {
        return 0;
      }
      byte[] pubKey = identity.ed25519PubRaw();
      byte[] privKey = identity.ed25519SecretKeyNaCl();
      int count = 0;
      for (int i = 0; i < pages.size(); i++) {
        Entry page = pages.get(i);
        session.dhtPutItem(pubKey, privKey, page, KarmaChainPages.salt(i));
        count += page.dictionary().get("e").list().size();
      }
      LOG.info(
          "Published karma chain: "
              + count
              + " of "
              + chain.size()
              + " entries in "
              + pages.size()
              + " pages");
      return count;
    } catch (Throwable t) {
      LOG.warn("Failed to publish karma chain", t);
      return 0;
    }
  }

  /** The head item of {@code chain}, or null if the chain is empty. */
  Entry buildManifest(List<KarmaChainEntry> chain) {
    List<Entry> pages = buildPages(chain);
    return pages.isEmpty() ? null : pages.get(0);
  }

  /** The items of {@code chain}, head first; see {@link KarmaChainPages#paginate(List)}. */
  List<Entry> buildPages(List<KarmaChainEntry> chain) {
    List<byte[]> pages = KarmaChainPages.paginate(chain);
    List<Entry> entries = new ArrayList<>(pages.size());
    for (byte[] page : pages) {
      entries.add(Entry.bdecode(page));
    }
    return entries;
  }
}
