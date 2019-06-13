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
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public class SampleEntry extends Box {
    protected final byte[] reserved;
    protected short data_reference_index;

    SampleEntry(int format) {
        super(format);
        boxes = new LinkedList<>();
        reserved = new byte[6];
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, 8, buf);
        buf.get(reserved);
        data_reference_index = buf.getShort();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.put(reserved);
        buf.putShort(data_reference_index);
        IO.write(ch, 8, buf);
    }
}
