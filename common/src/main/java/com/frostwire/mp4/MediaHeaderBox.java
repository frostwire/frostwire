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
public final class MediaHeaderBox extends FullBox {
    protected long creation_time;
    protected long modification_time;
    protected int timescale;
    protected long duration;
    protected short pre_defined;
    private byte[] language;

    MediaHeaderBox() {
        super(mdhd);
        language = new byte[2];
    }

    public String language() {
        return Bits.iso639(language);
    }

    public void language(String value) {
        language = value != null ? Bits.iso639(value) : new byte[2];
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, (version == 1 ? 28 : 16) + 4, buf);
        if (version == 1) {
            creation_time = buf.getLong();
            modification_time = buf.getLong();
            timescale = buf.getInt();
            duration = buf.getLong();
        } else { // version == 0
            creation_time = buf.getInt();
            modification_time = buf.getInt();
            timescale = buf.getInt();
            duration = buf.getInt();
        }
        buf.get(language);
        pre_defined = buf.getShort();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (version == 1) {
            buf.putLong(creation_time);
            buf.putLong(modification_time);
            buf.putInt(timescale);
            buf.putLong(duration);
        } else { // version == 0
            buf.putInt((int) creation_time);
            buf.putInt((int) modification_time);
            buf.putInt(timescale);
            buf.putInt((int) duration);
        }
        buf.put(language);
        buf.putShort(pre_defined);
        IO.write(ch, (version == 1 ? 28 : 16) + 4, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        if (version == 1) {
            s += 28;
        } else { // version == 0
            s += 16;
        }
        s += 2; // language
        s += 2; // pre_defined
        length(s);
    }
}
