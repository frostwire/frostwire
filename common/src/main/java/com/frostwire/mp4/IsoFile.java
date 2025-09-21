/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class IsoFile {
    private IsoFile() {
    }

    public static LinkedList<Box> head(RandomAccessFile in, ByteBuffer buf) throws IOException {
        in.seek(0);
        final InputChannel ch = new InputChannel(in.getChannel());
        final LinkedList<Box> boxes = new LinkedList<>();
        IsoMedia.read(ch, buf, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                if (b.parent == null) {
                    boxes.add(b);
                }
                return b.type != Box.mdat;
            }
        });
        in.seek(0);
        return boxes;
    }

    public static int count(RandomAccessFile in, final int type, ByteBuffer buf) throws IOException {
        in.seek(0);
        final InputChannel ch = new InputChannel(in.getChannel());
        final Int32 n = new Int32(0);
        IsoMedia.read(ch, buf, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) {
                if (b.type == type || type == 0) {
                    n.increment();
                }
                return true;
            }
        });
        in.seek(0);
        return n.get();
    }

    /**
     * This method replace the boxes with type {@code type} with a
     * free space box only in the header.
     *
     * @param in
     * @param type
     * @param buf
     * @throws IOException
     */
    public static void free(RandomAccessFile in, final int type, final ByteBuffer buf) throws IOException {
        in.seek(0);
        final InputChannel ch = new InputChannel(in.getChannel());
        final LinkedList<Box> boxes = new LinkedList<>();
        IsoMedia.read(ch, buf, new IsoMedia.OnBoxListener() {
            @Override
            public boolean onBox(Box b) throws IOException {
                if (b.parent == null) {
                    boxes.add(b);
                }
                if (b.type == type) {
                    if (b.parent != null) {
                        b.parent.boxes.remove(b);
                        b.parent.boxes.add(FreeSpaceBox.free(b.length()));
                        IO.skip(ch, b.length(), buf);
                    }
                }
                return b.type != Box.mdat;
            }
        });
        in.seek(0);
        OutputChannel out = new OutputChannel(in.getChannel());
        IsoMedia.write(out, boxes, buf, IsoMedia.OnBoxListener.ALL);
        in.seek(0);
    }
}
