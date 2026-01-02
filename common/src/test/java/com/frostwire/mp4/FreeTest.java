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

package com.frostwire.mp4;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public class FreeTest {

    @Test
    public void test1() throws IOException {
        File fIn = getTestResource("/com/frostwire/mp4/test_video.mp4");
        RandomAccessFile in = new RandomAccessFile(fIn, "rw");
        IsoFile.free(in, Box.udta, ByteBuffer.allocate(100 * 1024));
        IO.close(in);
    }

    private File getTestResource(String fileName) throws IOException {
        URL resource = getClass().getResource(fileName);
        if (resource == null) {
            throw new IOException("Test resource not found: " + fileName);
        }
        return new File(resource.getPath());
    }
}
