/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.gui.bittorrent.TransferSeedingRenderer.SeedIcon;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class TransferSeedingRendererTest {

  /** A transfer that fails the test if the renderer path queries it (libtorrent on the EDT). */
  private static SeedingHolder holderWithUntouchableDownload() {
    BTDownload download =
        (BTDownload)
            Proxy.newProxyInstance(
                BTDownload.class.getClassLoader(),
                new Class<?>[] {BTDownload.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getDisplayName")) {
                    return "fake";
                  }
                  throw new AssertionError("renderer queried the transfer: " + method.getName());
                });
    return new SeedingHolder(download);
  }

  @Test
  void seedIconComesFromPrecomputedStateOnly() {
    SeedingHolder holder = holderWithUntouchableDownload();

    holder.setSeedingState(false, true, false);
    assertEquals(SeedIcon.FADED, TransferSeedingRenderer.seedIcon(true, holder));

    holder.setSeedingState(true, true, false);
    assertEquals(SeedIcon.SOLID, TransferSeedingRenderer.seedIcon(true, holder));

    holder.setSeedingState(true, true, true);
    assertEquals(SeedIcon.LOADING, TransferSeedingRenderer.seedIcon(true, holder));

    holder.setSeedingState(true, false, false);
    assertEquals(SeedIcon.FADED, TransferSeedingRenderer.seedIcon(true, holder));
  }

  @Test
  void nonBittorrentTransfersAlwaysShowTheFadedIcon() {
    SeedingHolder holder = holderWithUntouchableDownload();
    holder.setSeedingState(true, true, false);

    assertEquals(SeedIcon.FADED, TransferSeedingRenderer.seedIcon(false, holder));
  }
}
