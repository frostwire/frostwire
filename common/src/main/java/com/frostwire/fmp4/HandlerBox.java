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
public final class HandlerBox extends FullBox {

    protected int pre_defined;
    protected int handler_type;
    protected int[] reserved;
    protected String name;

    HandlerBox() {
        super(hdlr);
    }

    @Override
    void read(InputChannel in, ByteBuffer buf) throws IOException {
        super.read(in, buf);

        long len = length() - 4;
        IO.read(in, Bits.l2i(len), buf);
        pre_defined = buf.getInt();
        handler_type = buf.getInt();
        reserved = new int[3];
        if (buf.remaining() > 0) {
            IO.get(buf, reserved);
            name = IO.utf8(buf);
        }
    }

    @Override
    void update() {
        long s = 20 + 4; // + 4 full box
        if (name != null) {
            s = Bits.l2u(s + Utf8.convert(name).length + 1);
        }
        length(s);
    }
}
