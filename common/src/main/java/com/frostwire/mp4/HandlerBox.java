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
public final class HandlerBox extends FullBox {
    protected final int[] reserved;
    protected int pre_defined;
    protected int handler_type;
    protected byte[] name;

    HandlerBox() {
        super(hdlr);
        reserved = new int[3];
        name = new byte[0];
    }

    public String name() {
        return Utf8.convert(name);
    }

    public void name(String value) {
        name = value != null ? Utf8.convert(value) : new byte[0];
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        int len = (int) (length() - 4);
        IO.read(ch, len, buf);
        pre_defined = buf.getInt();
        handler_type = buf.getInt();
        IO.get(buf, reserved);
        if (buf.remaining() > 0) {
            name = IO.str(buf);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(pre_defined);
        buf.putInt(handler_type);
        IO.put(buf, reserved);
        buf.put(name);
        buf.put((byte) 0);
        IO.write(ch, buf.position(), buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 20;
        s += name.length + 1;
        length(s);
    }
}
