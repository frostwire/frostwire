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
public final class DataEntryUrnBox extends FullBox {

    protected byte[] name;
    protected byte[] location;

    DataEntryUrnBox() {
        super(urn_);
    }

    public String name() {
        return name != null ? Utf8.convert(name) : null;
    }

    public void name(String value) {
        if (value != null) {
            name = Utf8.convert(value);
        }
    }

    public String location() {
        return location != null ? Utf8.convert(location) : null;
    }

    public void location(String value) {
        if (value != null) {
            location = Utf8.convert(value);
        }
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);

        long len = length() - 4;
        if (len > 0) {
            IO.read(ch, Bits.l2i(len), buf);
            if (buf.remaining() > 0) {
                name = IO.str(buf);
            }
            if (buf.remaining() > 0) {
                location = IO.str(buf);
            }
        }
    }

    @Override
    void update() {
        long s = 4; // + 4 full box
        if (name != null) {
            s = Bits.l2u(s + name.length + 1);
        }
        if (location != null) {
            s = Bits.l2u(s + location.length + 1);
        }
        length(s);
    }
}
