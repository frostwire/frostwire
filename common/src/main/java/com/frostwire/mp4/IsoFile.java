/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
