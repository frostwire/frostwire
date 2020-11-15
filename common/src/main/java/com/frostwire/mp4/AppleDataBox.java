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
public class AppleDataBox extends Box {
    protected int dataLength;
    protected int data4cc;
    protected int dataType;
    protected short dataCountry;
    protected short dataLanguage;

    AppleDataBox(int type) {
        super(type);
        data4cc = data;
    }

    public final int dataType() {
        return dataType;
    }

    public final void dataType(int value) {
        dataType = value;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, 16, buf);
        dataLength = buf.getInt();
        data4cc = buf.getInt();
        dataType = buf.getInt();
        dataCountry = buf.getShort();
        dataLanguage = buf.getShort();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.putInt(dataLength);
        buf.putInt(data4cc);
        buf.putInt(dataType);
        buf.putShort(dataCountry);
        buf.putShort(dataLanguage);
        IO.write(ch, 16, buf);
    }
}
