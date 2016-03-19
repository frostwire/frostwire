/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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
public abstract class FullBox extends Box {

    protected byte version;
    protected int flags;

    FullBox() {
    }

    @Override
    void read(InputChannel in, ByteBuffer buf) throws IOException {
        IO.read(in, 4, buf);
        int n = buf.getInt();

        version = Bits.int3(n);
        flags = Bits.int32((byte) 0, Bits.int3(n), Bits.int2(n), Bits.int0(n));
    }
}
