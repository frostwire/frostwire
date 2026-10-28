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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * @author gubatron
 * @author aldenml
 */
final class InputChannel implements ReadableByteChannel {
    private final ReadableByteChannel ch;
    private final long size;
    private long count;

    public InputChannel(ReadableByteChannel ch) {
        this(ch, -1);
    }

    /**
     * @param size the bytes {@code ch} holds from its current position, or -1 if unknown. Not
     *             needed for a file channel, which knows its own size.
     */
    public InputChannel(ReadableByteChannel ch, long size) {
        this.ch = ch;
        this.size = size;
        this.count = 0;
    }

    public long count() {
        return count;
    }

    /**
     * Bytes left to read, or {@link Long#MAX_VALUE} when the channel's size isn't known.
     */
    public long remaining() throws IOException {
        if (size >= 0) {
            return Math.max(0, size - count);
        }
        if (ch instanceof SeekableByteChannel) {
            SeekableByteChannel s = (SeekableByteChannel) ch;
            return Math.max(0, s.size() - s.position());
        }
        return Long.MAX_VALUE;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int n = ch.read(dst);
        if (n > 0) {
            count += n;
        }
        return n;
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public void close() throws IOException {
        ch.close();
    }
}
