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
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public class ContainerBox extends Box {

    private final LinkedList<Box> boxes;

    ContainerBox(int type) {
        super(type);
        this.boxes = new LinkedList<>();
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
    }

    @Override
    LinkedList<Box> boxes() {
        return boxes;
    }

    @Override
    void update() {
        long s = 0;
        for (Box b : boxes) {
            b.update();
            if (b.size == 1) {
                s = Bits.l2u(s + b.largesize);
            } else if (b.size == 0) {
                throw new UnsupportedOperationException();
            } else {
                s = Bits.l2u(s + b.size);
            }
        }
        length(s);
    }
}
