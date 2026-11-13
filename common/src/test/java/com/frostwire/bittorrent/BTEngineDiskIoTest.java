/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * libtorrent 2.1 made its pread() back-end the default; asking for "the default" would have moved
 * Windows off the memory-mapped files it used with libtorrent 2.0. Each system keeps what it had.
 */
class BTEngineDiskIoTest {

  @Test
  void macOsLinuxAndAndroidKeepThePosixBackend() {
    assertEquals(BTEngine.DiskIo.POSIX, BTEngine.diskIoFor(true, false));
  }

  @Test
  void windowsKeepsMemoryMappedFiles() {
    assertEquals(BTEngine.DiskIo.MMAP, BTEngine.diskIoFor(false, true));
  }

  @Test
  void otherSystemsGetLibtorrentsDefault() {
    assertEquals(BTEngine.DiskIo.DEFAULT, BTEngine.diskIoFor(false, false));
  }
}
