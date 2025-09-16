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

/**
 * @author gubatron
 * @author aldenml
 */
public final class FileTypeBox extends Box {
    protected int major_brand;
    protected int minor_version;
    protected int[] compatible_brands;

    FileTypeBox() {
        super(ftyp);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, (int) length(), buf);
        major_brand = buf.getInt();
        minor_version = buf.getInt();
        compatible_brands = new int[buf.remaining() / 4];
        IO.get(buf, compatible_brands);
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.putInt(major_brand);
        buf.putInt(minor_version);
        IO.put(buf, compatible_brands);
        IO.write(ch, buf.position(), buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // major_brand
        s += 4; // minor_version
        s += compatible_brands.length * 4;
        length(s);
    }
}
