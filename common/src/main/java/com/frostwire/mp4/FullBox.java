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
public class FullBox extends Box {
    protected byte version;
    protected int flags;

    FullBox(int type) {
        super(type);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, 4, buf);
        int n = buf.getInt();
        version = Bits.int3(n);
        flags = Bits.int32((byte) 0, Bits.int2(n), Bits.int1(n), Bits.int0(n));
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        int n = Bits.int32(version, Bits.int2(flags), Bits.int1(flags), Bits.int0(flags));
        buf.putInt(n);
        IO.write(ch, 4, buf);
    }
}
