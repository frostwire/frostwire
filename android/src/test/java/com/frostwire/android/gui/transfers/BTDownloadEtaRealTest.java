/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The time left of a real transfer that downloads only some of the files of its torrent. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class BTDownloadEtaRealTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void aCompleteTransferOfOnlyOneFileHasNoTimeLeft() throws Exception {
    // two.txt comes first in the torrent: only one.mp3 is wanted
    boolean[] onlyOneMp3 = {false, true};
    try (RealTransfer rt = RealTransfer.start(tmp.newFolder("root").toPath(), onlyOneMp3, true)) {
      rt.awaitComplete();

      assertEquals(100, rt.ui.getProgress());
      assertEquals(0, rt.dl.getETA());
    }
  }
}
