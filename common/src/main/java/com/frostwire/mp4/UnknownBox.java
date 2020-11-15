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
public final class UnknownBox extends Box {
    private static final int MAX_DATA_SIZE = 1024 * 1024; // 1MB
    protected byte[] data;

    UnknownBox(int type) {
        super(type);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        int len = (int) length();
        if (len > 0) {
            if (len <= MAX_DATA_SIZE) {
                data = new byte[len];
                buf = ByteBuffer.wrap(data);
                IO.read(ch, len, buf);
            } else {
                IO.skip(ch, len, buf);
            }
        } else {
            IO.skip(ch, buf);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        if (data != null) {
            buf = ByteBuffer.wrap(data);
            IO.write(ch, data.length, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        if (data != null) {
            s += data.length;
        }
        length(s);
    }
}
