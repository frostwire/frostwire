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

/**
 * @author gubatron
 * @author aldenml
 */
public final class Mp4Info {

    Mp4Info() {
    }

    public int majorBrand;
    public int[] compatibleBrands;
    public String title;
    public String author;
    public String album;
    public byte[] jpg;

    public static Mp4Info audio(String title, String author, String album, byte[] jpg) {
        Mp4Info inf = new Mp4Info();

        inf.majorBrand = Box.M4A_;
        inf.compatibleBrands = new int[]{Box.M4A_, Box.mp42, Box.isom, Box.zero};
        inf.title = title;
        inf.author = author;
        inf.album = album;
        inf.jpg = jpg;

        return inf;
    }
}
