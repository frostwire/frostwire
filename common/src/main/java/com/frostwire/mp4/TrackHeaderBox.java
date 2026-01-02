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

/**
 * @author gubatron
 * @author aldenml
 */
public final class TrackHeaderBox extends FullBox {
    protected final int[] reserved2;
    protected final int[] matrix;
    protected long creation_time;
    protected long modification_time;
    protected int reserved1;
    protected long duration;
    protected short layer;
    protected short alternate_group;
    protected short volume;
    protected short reserved3;
    protected int width;
    protected int height;
    private int track_ID;

    TrackHeaderBox() {
        super(tkhd);
        reserved2 = new int[2];
        matrix = new int[]{0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
    }

    public int trackId() {
        return track_ID;
    }

    public void trackId(int value) {
        track_ID = value;
    }

    public boolean enabled() {
        return (flags & 0x1) == 0x1;
    }

    public void enabled(boolean value) {
        if (value) {
            flags = (flags | 0x1);
        } else {
            flags = (flags & ~0x1);
        }
    }

    public boolean inMovie() {
        return (flags & 0x2) == 0x2;
    }

    public void inMovie(boolean value) {
        if (value) {
            flags = (flags | 0x2);
        } else {
            flags = (flags & ~0x2);
        }
    }

    public boolean inPreview() {
        return (flags & 0x4) == 0x4;
    }

    public void inPreview(boolean value) {
        if (value) {
            flags = (flags | 0x4);
        } else {
            flags = (flags & ~0x4);
        }
    }

    public boolean inPoster() {
        return (flags & 0x8) == 0x8;
    }

    public void inPoster(boolean value) {
        if (value) {
            flags = (flags | 0x8);
        } else {
            flags = (flags & ~0x8);
        }
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, (version == 1 ? 32 : 20) + 60, buf);
        if (version == 1) {
            creation_time = buf.getLong();
            modification_time = buf.getLong();
            track_ID = buf.getInt();
            reserved1 = buf.getInt();
            duration = buf.getLong();
        } else { // version == 0
            creation_time = buf.getInt();
            modification_time = buf.getInt();
            track_ID = buf.getInt();
            reserved1 = buf.getInt();
            duration = buf.getInt();
        }
        IO.get(buf, reserved2);
        layer = buf.getShort();
        alternate_group = buf.getShort();
        volume = buf.getShort();
        reserved3 = buf.getShort();
        IO.get(buf, matrix);
        width = buf.getInt();
        height = buf.getInt();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (version == 1) {
            buf.putLong(creation_time);
            buf.putLong(modification_time);
            buf.putInt(track_ID);
            buf.putInt(reserved1);
            buf.putLong(duration);
        } else { // version == 0
            buf.putInt((int) creation_time);
            buf.putInt((int) modification_time);
            buf.putInt(track_ID);
            buf.putInt(reserved1);
            buf.putInt((int) duration);
        }
        IO.put(buf, reserved2);
        buf.putShort(layer);
        buf.putShort(alternate_group);
        buf.putShort(volume);
        buf.putShort(reserved3);
        IO.put(buf, matrix);
        buf.putInt(width);
        buf.putInt(height);
        IO.write(ch, (version == 1 ? 32 : 20) + 60, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 60;
        if (version == 1) {
            s += 32;
        } else { // version == 0
            s += 20;
        }
        length(s);
    }
}
