/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.libtorrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.frostwire.jlibtorrent.LibTorrent;
import com.frostwire.jlibtorrent.Sha1Hash;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The libtorrent native library loads inside every Robolectric sandbox, whatever its SDK. */
@RunWith(RobolectricTestRunner.class)
public class LibtorrentOnJvmTest {
  private static final String HASH = "0123456789012345678901234567890123456789";

  @Test
  @Config(sdk = 34)
  public void loadsInTheDefaultSandbox() {
    assertEquals(HASH, new Sha1Hash(HASH).toHex());
    assertTrue(LibTorrent.versionNum() > 0);
  }

  @Test
  @Config(sdk = 28)
  public void loadsInAnOlderSdkSandbox() {
    assertEquals(HASH, new Sha1Hash(HASH).toHex());
  }

  @Test
  @Config(sdk = 30)
  public void loadsInAThirdSandbox() {
    assertEquals(HASH, new Sha1Hash(HASH).toHex());
  }
}
