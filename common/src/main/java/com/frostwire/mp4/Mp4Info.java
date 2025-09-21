/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.mp4;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Mp4Info {
    public int majorBrand;
    public int[] compatibleBrands;
    public String title;
    public String author;
    public String album;
    public byte[] jpg;
    Mp4Info() {
    }

    public static Mp4Info audio(String title, String author, String album, byte[] jpg) {
        Mp4Info inf = new Mp4Info();
        inf.majorBrand = Box.M4A_;
        inf.compatibleBrands = new int[]{Box.M4A_, Box.mp42, Box.isom, Box.zero};
        inf.fill(title, author, album, jpg);
        return inf;
    }

    public static Mp4Info avc(String title, String author, String album, byte[] jpg) {
        Mp4Info inf = new Mp4Info();
        inf.majorBrand = Box.MP4_;
        inf.compatibleBrands = new int[]{Box.iso6, Box.avc1, Box.mp41, Box.zero};
        inf.fill(title, author, album, jpg);
        return inf;
    }

    private void fill(String title, String author, String album, byte[] jpg) {
        this.title = title;
        this.author = author;
        this.album = album;
        this.jpg = jpg;
    }
}
