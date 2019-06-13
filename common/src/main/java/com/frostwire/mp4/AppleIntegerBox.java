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
public class AppleIntegerBox extends AppleDataBox {
    private byte[] value;

    AppleIntegerBox(int type) {
        super(type);
        dataType = 15;
    }

    public int value() {
        if (value != null) {
            ByteBuffer t = ByteBuffer.allocate(4);
            t.put(value);
            t.flip();
            return t.getInt();
        } else {
            return 0;
        }
    }

    public void value(int value) {
        if (value <= 127 && value > -128) {
            this.value = new byte[]{Bits.int0(value)};
        } else if (value <= 32767 && value > -32768) {
            this.value = new byte[]{Bits.int1(value), Bits.int0(value)};
        } else if (value <= 8388607 && value > -838860) {
            this.value = new byte[]{Bits.int2(value), Bits.int1(value), Bits.int0(value)};
        } else {
            this.value = new byte[]{Bits.int3(value), Bits.int2(value), Bits.int1(value), Bits.int0(value)};
        }
        dataLength = this.value.length + 16;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        int len = (int) (length() - 16);
        if (len != 0) {
            IO.read(ch, len, buf);
            value = new byte[len];
            buf.get(value);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (value != null) {
            buf.put(value);
            IO.write(ch, value.length, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 16; // apple data box
        if (value != null) {
            s += value.length;
        }
        length(s);
    }
}
