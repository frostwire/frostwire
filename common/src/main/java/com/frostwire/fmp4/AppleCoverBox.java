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

package com.frostwire.fmp4;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public class AppleCoverBox extends AppleDataBox {

    private byte[] value;

    AppleCoverBox() {
        super(covr);
        this.dataType = 1;
    }

    public byte[] value() {
        return value;
    }

    public void value(byte[] value) {
        this.value = value;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);

        long len = length() - 16;
        if (len > 0) {
            buf.clear();
            int ilen = Bits.l2i(len);
            if (buf.capacity() < ilen) {
                buf = ByteBuffer.allocate(ilen);
            }
            IO.read(ch, ilen, buf);
            value = new byte[ilen];
            buf.get(value);
        }
    }

    @Override
    void update() {
        long s = 16; // + 16 apple data box
        if (value != null) {
            s = Bits.l2u(s + value.length);
        }
        length(s);
    }
}
