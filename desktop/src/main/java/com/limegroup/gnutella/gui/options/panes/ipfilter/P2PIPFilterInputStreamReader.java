/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.options.panes.ipfilter;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class P2PIPFilterInputStreamReader implements IPFilterInputStreamReader {
    private final static Pattern P2P_LINE_PATTERN = Pattern.compile("(.*)\\:(.*)\\-(.*)$", java.util.regex.Pattern.COMMENTS);
    private final BufferedReader br;
    private InputStream is;
    private int bytesRead;

    public P2PIPFilterInputStreamReader(File uncompressedFile) {
        try {
            this.is = new FileInputStream(uncompressedFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        br = new BufferedReader(new InputStreamReader(is));
        bytesRead = 0;
    }

    @Override
    public IPRange readLine() {
        try {
            if (is.available() > 0) {
                String line = br.readLine();
                bytesRead += line.length();
                while (line.startsWith("#") && is.available() > 0) {
                    line = br.readLine();
                    bytesRead += line.length();
                }
                Matcher matcher = P2P_LINE_PATTERN.matcher(line);
                if (matcher.find()) {
                    return new IPRange(matcher.group(1), matcher.group(2), matcher.group(3));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int bytesRead() {
        return bytesRead;
    }

    @Override
    public int available() {
        try {
            return is.available();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public void close() {
        try {
            is.close();
            br.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
