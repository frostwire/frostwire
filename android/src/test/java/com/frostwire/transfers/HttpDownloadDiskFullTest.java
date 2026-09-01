/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HttpDownloadDiskFullTest {

  @Test
  public void noSpaceLeftIsDiskFullAndSafFailureIsNot() {
    assertEquals(
        TransferState.ERROR_DISK_FULL,
        BaseHttpDownload.downloadErrorState(new IOException("No space left on device")));
    assertEquals(
        TransferState.ERROR_DISK_FULL,
        BaseHttpDownload.failedMoveState(new IOException("No space left on device")));
    assertEquals(TransferState.ERROR_MOVING_INCOMPLETE, BaseHttpDownload.failedMoveState(null));
    assertEquals(
        TransferState.ERROR_MOVING_INCOMPLETE,
        BaseHttpDownload.failedMoveState(new IOException("Permission denied")));
    assertEquals(
        TransferState.ERROR_MOVING_INCOMPLETE,
        BaseHttpDownload.failedMoveState(
            new IOException(
                "Unable to obtain document for file: content://com.android.externalstorage.documents/tree/primary%3ADownload/track.mp3")));
  }

  @Test
  public void moveAndCompleteDoesNotInferDiskFullFromUsableSpace() throws Exception {
    String source = readCommon("src/main/java/com/frostwire/transfers/BaseHttpDownload.java");
    assertFalse(source.contains("getUsableSpace"));
    assertTrue(source.contains("failedMoveState(fs.lastCopyError())"));
    assertTrue(source.contains("isNoSpaceLeft"));
  }

  private static String readCommon(String relativePath) throws Exception {
    Path cwd = Path.of(System.getProperty("user.dir"));
    Path[] candidates = {
      cwd.resolve("..").resolve("common").resolve(relativePath),
      cwd.resolve("common").resolve(relativePath),
      cwd.getParent().resolve("common").resolve(relativePath)
    };
    for (Path file : candidates) {
      if (Files.isRegularFile(file)) {
        return Files.readString(file, StandardCharsets.UTF_8);
      }
    }
    throw new IOException("missing " + relativePath + " from " + cwd);
  }
}
