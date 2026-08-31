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

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseHttpDownloadDiskFullTest {

    @Test
    public void downloadErrorMapsNoSpaceLeftToDiskFull() {
        assertEquals(TransferState.ERROR_DISK_FULL,
                BaseHttpDownload.downloadErrorState(new IOException("No space left on device")));
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
    public void failedMoveUsesDiskFullWhenDestinationHasLessSpaceThanTheFile() {
        assertEquals(TransferState.ERROR_DISK_FULL,
                BaseHttpDownload.failedMoveState(1024, 16));
    }

    @Test
    public void failedMoveKeepsMovingIncompleteWhenThereIsSpace() {
        assertEquals(TransferState.ERROR_MOVING_INCOMPLETE,
                BaseHttpDownload.failedMoveState(1024, 4096));
    }
}
