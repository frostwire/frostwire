/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.function.LongSupplier;

/**
 * Remembers a {@link BlockHeaderSource}'s chain tip for a while, so verifying many peers' karma
 * chains asks the Bitcoin APIs for the tip about once a minute instead of once per chain. Blocks
 * pass straight through ({@link HttpBlockHeaderFetcher} caches them itself). An unknown tip ({@code
 * -1}) isn't remembered, so the next call asks again.
 */
public final class TipCachingBlockHeaderSource implements BlockHeaderSource {

  /** How long a known tip is reused. Bitcoin adds a block about every ten minutes. */
  public static final long DEFAULT_TIP_TTL_MS = 60_000;

  private final BlockHeaderSource delegate;
  private final long ttlNanos;
  private final LongSupplier nanoClock;
  private long tip = -1;
  private long tipFetchedAt;

  public TipCachingBlockHeaderSource(BlockHeaderSource delegate) {
    this(delegate, DEFAULT_TIP_TTL_MS, System::nanoTime);
  }

  TipCachingBlockHeaderSource(BlockHeaderSource delegate, long ttlMs, LongSupplier nanoClock) {
    if (delegate == null || nanoClock == null || ttlMs <= 0) {
      throw new IllegalArgumentException("delegate, clock and a positive ttl are required");
    }
    this.delegate = delegate;
    this.ttlNanos = ttlMs * 1_000_000L;
    this.nanoClock = nanoClock;
  }

  @Override
  public BitcoinBlockReference getBlock(long height) {
    return delegate.getBlock(height);
  }

  @Override
  public long getChainTipHeight() {
    long now = nanoClock.getAsLong();
    synchronized (this) {
      if (tip >= 0 && now - tipFetchedAt < ttlNanos) {
        return tip;
      }
    }
    // Ask outside the lock: the delegate may wait on the network.
    long fresh = delegate.getChainTipHeight();
    synchronized (this) {
      if (fresh >= 0 && fresh >= tip) {
        tip = fresh;
        tipFetchedAt = now;
      }
      return fresh >= 0 ? fresh : -1;
    }
  }
}
