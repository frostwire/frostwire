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
public class EntryBaseBox extends FullBox {

    EntryBaseBox(int type) {
        super(type);
    }

    @Override
    final void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);

        writeFields(ch, buf);
        int entry_count = entryCount();
        int entry_size = entrySize();

        if (entry_count > 0) {
            for (int i = 0; i < entry_count; i++) {
                if (buf.position() > 0 && buf.remaining() < entry_size) {
                    IO.write(ch, buf.position(), buf);
                }
                putEntry(i, buf);
            }
            if (buf.position() > 0) {
                IO.write(ch, buf.position(), buf);
            }
        }
    }

    void writeFields(OutputChannel ch, ByteBuffer buf) throws IOException {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }

    int entryCount() {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }

    int entrySize() {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }

    void putEntry(int i, ByteBuffer buf) {
        throw new UnsupportedOperationException(Bits.make4cc(type));
    }
}
