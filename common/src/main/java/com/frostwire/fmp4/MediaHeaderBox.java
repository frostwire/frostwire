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
public final class MediaHeaderBox extends FullBox {

    protected long creation_time;
    protected long modification_time;
    protected int timescale;
    protected long duration;
    protected byte[] language;
    protected short pre_defined;

    MediaHeaderBox() {
        super(mdhd);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);

        if (version == 1) {
            IO.read(ch, 28, buf);
            creation_time = buf.getLong();
            modification_time = buf.getLong();
            timescale = buf.getInt();
            duration = buf.getLong();
        } else { // version == 0
            IO.read(ch, 16, buf);
            creation_time = buf.getInt();
            modification_time = buf.getInt();
            timescale = buf.getInt();
            duration = buf.getInt();
        }

        IO.read(ch, 4, buf);
        language = new byte[2];
        buf.get(language);
        pre_defined = buf.getShort();
    }

    @Override
    void update() {
        long s = 4 + 4; // + 4 full box
        if (version == 1) {
            s += 28;
        } else { // version == 0
            s += 16;
        }
        length(s);
    }
}
