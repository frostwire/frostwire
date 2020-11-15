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
public final class HintMediaHeaderBox extends FullBox {
    protected short maxPDUsize;
    protected short avgPDUsize;
    protected int maxbitrate;
    protected int avgbitrate;
    protected int reserved;

    HintMediaHeaderBox() {
        super(hmhd);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 16, buf);
        maxPDUsize = buf.getShort();
        avgPDUsize = buf.getShort();
        maxbitrate = buf.getInt();
        avgbitrate = buf.getInt();
        reserved = buf.getInt();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putShort(maxPDUsize);
        buf.putShort(avgPDUsize);
        buf.putInt(maxbitrate);
        buf.putInt(avgbitrate);
        buf.putInt(reserved);
        IO.write(ch, 16, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 16;
        length(s);
    }
}
