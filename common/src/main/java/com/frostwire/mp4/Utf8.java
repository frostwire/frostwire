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

import java.nio.charset.StandardCharsets;

final class Utf8 {
    private Utf8() {
    }

    public static byte[] convert(String s) {
        if (s != null) {
            return s.getBytes(StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    public static String convert(byte[] b) {
        if (b != null) {
            return new String(b, StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }
}
