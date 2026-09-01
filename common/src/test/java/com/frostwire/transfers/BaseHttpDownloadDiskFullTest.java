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

import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.transfers.BaseHttpDownload;
import com.frostwire.transfers.TransferState;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BaseHttpDownloadDiskFullTest {

    @Test
    public void downloadErrorMapsNoSpaceLeftToDiskFull() {
        assertEquals(TransferState.ERROR_DISK_FULL,
                BaseHttpDownload.downloadErrorState(new IOException("No space left on device")));
        assertEquals(TransferState.ERROR_DISK_FULL,
                BaseHttpDownload.downloadErrorState(new IOException("copy failed",
                        new IOException("No space left on device"))));
    }

    @Test
    public void downloadErrorKeepsGenericError() {
        assertEquals(TransferState.ERROR,
                BaseHttpDownload.downloadErrorState(new IOException("broken pipe")));
    }

    @Test
    public void downloadErrorMapsTimeoutAndUnknownHost() {
        assertEquals(TransferState.ERROR_CONNECTION_TIMED_OUT,
                BaseHttpDownload.downloadErrorState(new SocketTimeoutException("read timed out")));
        assertEquals(TransferState.ERROR_CONNECTION_TIMED_OUT,
                BaseHttpDownload.downloadErrorState(new SSLException("handshake")));
        assertEquals(TransferState.ERROR_NO_INTERNET,
                BaseHttpDownload.downloadErrorState(new UnknownHostException("example.invalid")));
    }

    @Test
    public void failedMoveUsesDiskFullOnlyForNoSpaceLeft() {
        assertEquals(TransferState.ERROR_DISK_FULL,
                BaseHttpDownload.failedMoveState(new IOException("No space left on device")));
        assertEquals(TransferState.ERROR_MOVING_INCOMPLETE,
                BaseHttpDownload.failedMoveState(new IOException("Permission denied")));
        assertEquals(TransferState.ERROR_MOVING_INCOMPLETE,
                BaseHttpDownload.failedMoveState(new IOException("Read-only file system")));
    }

    @Test
    public void safStyleCopyFailureWithoutIoExceptionStaysMovingIncomplete() {
        // DocumentFile copy often returns false with no Java ENOSPC.
        assertEquals(TransferState.ERROR_MOVING_INCOMPLETE,
                BaseHttpDownload.failedMoveState(null));
        assertEquals(TransferState.ERROR_MOVING_INCOMPLETE,
                BaseHttpDownload.failedMoveState(new IOException(
                        "Unable to obtain document for file: /storage/0000-0000/Music/track.mp3")));
    }

    @Test
    public void defaultFileSystemCopyFailureIsNotReportedAsDiskFull() throws Exception {
        DefaultFileSystem fs = new DefaultFileSystem();
        File src = File.createTempFile("fw-src", ".bin");
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write("payload".getBytes(StandardCharsets.UTF_8));
        }
        File notADir = File.createTempFile("fw-notdir", ".bin");
        File dest = new File(notADir, "child.bin");
        try {
            assertFalse(fs.copy(src, dest));
            assertNotNull(fs.lastCopyError());
            assertEquals(TransferState.ERROR_MOVING_INCOMPLETE,
                    BaseHttpDownload.failedMoveState(fs.lastCopyError()));
        } finally {
            src.delete();
            notADir.delete();
        }
    }
}
