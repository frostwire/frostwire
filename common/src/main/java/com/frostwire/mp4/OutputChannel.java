/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import java.nio.channels.WritableByteChannel;

/**
 * @author gubatron
 * @author aldenml
 */
final class OutputChannel implements WritableByteChannel {
    private final WritableByteChannel ch;
    private long count;

    public OutputChannel(WritableByteChannel ch) {
        this.ch = ch;
        this.count = 0;
    }

    public long count() {
        return count;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int n = ch.write(src);
        if (n > 0) {
            count += n;
        }
        return n;
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public void close() throws IOException {
        ch.close();
    }
}
