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
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MovieHeaderBox extends FullBox {
    protected final int[] reserved2;
    protected final int[] matrix;
    protected final int[] pre_defined;
    protected long creation_time;
    protected long modification_time;
    protected int timescale;
    protected long duration;
    protected int rate;
    protected short volume;
    protected short reserved1;
    protected int next_track_ID;

    MovieHeaderBox() {
        super(mvhd);
        rate = 0x00010000;
        volume = 0x0100;
        reserved2 = new int[2];
        matrix = new int[]{0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
        pre_defined = new int[6];
    }

    public int timescale() {
        return timescale;
    }

    public void timescale(int value) {
        timescale = value;
    }

    public long duration() {
        return duration;
    }

    public void duration(long value) {
        duration = value;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, (version == 1 ? 28 : 16) + 80, buf);
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
        rate = buf.getInt();
        volume = buf.getShort();
        reserved1 = buf.getShort();
        IO.get(buf, reserved2);
        IO.get(buf, matrix);
        IO.get(buf, pre_defined);
        next_track_ID = buf.getInt();
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
        buf.putInt(rate);
        buf.putShort(volume);
        buf.putShort(reserved1);
        IO.put(buf, reserved2);
        IO.put(buf, matrix);
        IO.put(buf, pre_defined);
        buf.putInt(next_track_ID);
        IO.write(ch, (version == 1 ? 28 : 16) + 80, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 80;
        if (version == 1) {
            s += 28;
        } else { // version == 0
            s += 16;
        }
        length(s);
    }
}
