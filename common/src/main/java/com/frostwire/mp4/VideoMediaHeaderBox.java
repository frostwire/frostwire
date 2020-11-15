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
public final class VideoMediaHeaderBox extends FullBox {
    protected final short[] opcolor;
    protected short graphicsmode;

    VideoMediaHeaderBox() {
        super(vmhd);
        opcolor = new short[]{0, 0, 0};
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 8, buf);
        graphicsmode = buf.getShort();
        IO.get(buf, opcolor);
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putShort(graphicsmode);
        IO.put(buf, opcolor);
        IO.write(ch, 8, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 2; // graphicsmode
        s += 3 * 2; // opcolor
        length(s);
    }
}
